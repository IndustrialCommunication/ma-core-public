/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.dao;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.mutable.MutableLong;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.serotonin.m2m2.vo.DataPointVO;

public class MigratingPointValueDao extends DelegatingPointValueDao implements AutoCloseable {

    /**
     * Separate log file is configured for this logger
     */
    private final Logger log = LoggerFactory.getLogger("pointValueMigration");
    /**
     * Map key is series id
     */
    private final ConcurrentMap<Integer, MigrationStatus> migratedSeries = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<Integer> seriesQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean fullyMigrated = new AtomicBoolean();
    private final AtomicBoolean stopFlag = new AtomicBoolean();
    private final AtomicLong migratedCount = new AtomicLong();
    private final AtomicLong skippedCount = new AtomicLong();
    private final AtomicLong migratedTotal = new AtomicLong();
    private final MigrationThread[] threads;
    private final Predicate<DataPointVO> migrationFilter;
    private final DataPointDao dataPointDao;

    public enum MigrationStatus {
        NOT_STARTED,
        RUNNING,
        MIGRATED,
        SKIPPED
    }

    // TODO add time filter
    public MigratingPointValueDao(PointValueDao primary, PointValueDao secondary, DataPointDao dataPointDao, Predicate<DataPointVO> migrationFilter) {
        super(primary, secondary);
        // TODO configurable
        this.threads = new MigrationThread[4];
        this.dataPointDao = dataPointDao;
        this.migrationFilter = migrationFilter;
    }

    @PostConstruct
    private void postConstruct() {
        try (var stream = dataPointDao.streamSeriesIds()) {
            stream.forEach(seriesId -> {
                migratedSeries.put(seriesId, MigrationStatus.NOT_STARTED);
                seriesQueue.add(seriesId);
                migratedTotal.incrementAndGet();
            });
        }

        for (int i = 0; i < threads.length; i++) {
            MigrationThread thread = new MigrationThread(i);
            this.threads[i] = thread;
            thread.start();
        }
    }

    @Override
    public void close() throws Exception {
        stopFlag.set(true);
        for (MigrationThread thread : threads) {
            if (thread != null) {
                thread.join(TimeUnit.SECONDS.toMillis(60));
                thread.interrupt();
            }
        }
    }

    @Override
    public boolean handleWithPrimary(Operation operation) {
        if (fullyMigrated.get()) {
            return true;
        }
        throw new UnsupportedOperationException();
    }

    public boolean handleWithPrimary(DataPointVO vo, Operation operation) {
        @Nullable MigrationStatus migrated = migratedSeries.get(vo.getSeriesId());
        if (migrated == null || migrated == MigrationStatus.MIGRATED) {
            // series is a new series, or has been migrated
            return true;
        }

        if (operation == Operation.DELETE) {
            throw new UnsupportedOperationException();
        }
        return false;
    }

    private class MigrationThread extends Thread {

        private MigrationThread(int threadId) {
            super();
            setName(String.format("pv-migration-%03d", threadId));
        }

        @Override
        public void run() {
            log.info("Migration thread starting");

            Integer seriesId = null;
            while (!stopFlag.get() && (seriesId = seriesQueue.poll()) != null) {
                MigrationStatus previous = migratedSeries.put(seriesId, MigrationStatus.RUNNING);
                if (previous != MigrationStatus.NOT_STARTED) {
                    throw new IllegalStateException("Migration should not have stared for series: " + seriesId);
                }

                try {
                    migrateSeries(seriesId);
                } catch (Exception e) {
                    log.error("Error migrating series {}", seriesId, e);
                    migratedSeries.put(seriesId, MigrationStatus.NOT_STARTED);
                    seriesQueue.add(seriesId);
                }
            }

            if (seriesId == null) {
                log.info("Migration thread stopped, migration is complete. {}/{} series migrated, {} skipped.", migratedCount.get(), migratedTotal.get(), skippedCount.get());
            } else {
                log.warn("Migration thread stopped, migration is incomplete.  {}/{} series migrated, {} skipped.", migratedCount.get(), migratedTotal.get(), skippedCount.get());
            }
        }

        private void migrateSeries(Integer seriesId) {
            DataPointVO point = dataPointDao.getBySeriesId(seriesId);
            if (point == null || !migrationFilter.test(point)) {
                migratedSeries.put(seriesId, MigrationStatus.SKIPPED);
                skippedCount.incrementAndGet();
                return;
            }

            MutableLong sampleCount = new MutableLong();
            // initial pass at copying data
            Long lastTimestamp = copyPointValues(point, null, sampleCount);
            // copy again as the first run might have taken a long time
            lastTimestamp = copyPointValues(point, lastTimestamp, sampleCount);

            Long lastTimestampFinal = lastTimestamp;

            // do a final copy inside the compute method so inserts are blocked
            migratedSeries.computeIfPresent(point.getSeriesId(), (k,v) -> {
                copyPointValues(point, lastTimestampFinal, sampleCount);
                return MigrationStatus.MIGRATED;
            });
            migratedCount.incrementAndGet();

            log.info("Series {} complete. Copied {} samples for data point {}. {}/{} series migrated, {} skipped.",
                    point.getSeriesId(), sampleCount.longValue(), point.getXid(), migratedCount.get(), migratedTotal.get(), skippedCount.get());
        }

        private Long copyPointValues(DataPointVO point, Long from, MutableLong sampleCount) {
            // TODO configurable
            int batchSize = 10_000;
            MutableLong lastTimestamp = new MutableLong();
            MutableLong count = new MutableLong();

            // TODO overload that allows specifying chunk size
            try (var stream = secondary.streamPointValues(point, from, null, null, TimeOrder.ASCENDING)) {

                // TODO save methods should support Annotated PVT if source is null?
                // TODO make savePointValues batch insert size configurable
                primary.savePointValues(stream.map(pv -> {
                    lastTimestamp.setValue(pv.getTime());
                    count.incrementAndGet();
                    return new BatchPointValueImpl(point, pv, null);
                }));
            }

            sampleCount.add(count.longValue());
            return lastTimestamp.longValue();
        }
    }
}
