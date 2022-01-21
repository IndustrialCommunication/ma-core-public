/*
 * Copyright (C) 2022 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.dao.migration;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.infiniteautomation.mango.pointvalue.generator.BrownianPointValueGenerator;
import com.serotonin.m2m2.MangoTestBase;
import com.serotonin.m2m2.MockMangoLifecycle;
import com.serotonin.m2m2.MockPointValueDao;
import com.serotonin.m2m2.db.dao.BatchPointValueImpl;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.db.dao.PointValueDao;
import com.serotonin.m2m2.db.dao.PointValueDao.TimeOrder;
import com.serotonin.m2m2.db.dao.migration.progress.MigrationProgressDao;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.vo.dataPoint.MockPointLocatorVO;
import com.serotonin.timer.AbstractTimer;
import com.serotonin.timer.SimulationTimer;

/**
 * @author Jared Wiltshire
 */
public class MigrationPointValueDaoTest extends MangoTestBase {

    MigrationPointValueDao migrationPointValueDao;
    TestMigrationConfig migrationConfig;
    MockPointValueDao source;
    MockPointValueDao destination;
    SimulationTimer timer;

    @Override
    public void before() {
        super.before();

        ApplicationContext context = MangoTestBase.lifecycle.getRuntimeContext();
        this.migrationPointValueDao = context.getBean(MigrationPointValueDao.class);
        this.migrationConfig = context.getBean(TestMigrationConfig.class);
        this.source = context.getBean("source", MockPointValueDao.class);
        this.destination = context.getBean("destination", MockPointValueDao.class);
        this.timer = context.getBean(SimulationTimer.class);
    }

    @Override
    public void after() {
        super.after();
        this.migrationPointValueDao.reset();

        // ensure point data is deleted, MangoTestBase calls this also, but it may not be called for both source and destination
        this.source.deleteAllPointData();
        this.destination.deleteAllPointData();
    }

    @Test
    public void singleValue() throws ExecutionException, InterruptedException, TimeoutException {
        var dataSource = createMockDataSource();
        var point = createMockDataPoint(dataSource, new MockPointLocatorVO());

        Instant from = Instant.ofEpochMilli(0L);
        var sourceValues = List.of(new PointValueTime(0.0D, from.toEpochMilli()));
        var batchInsertValues = sourceValues.stream().map(v -> new BatchPointValueImpl(point, v))
                .collect(Collectors.toList());
        source.savePointValues(batchInsertValues.stream());

        migrationPointValueDao.startMigration();
        migrationPointValueDao.migrationFinished().get(30, TimeUnit.SECONDS);

        var destinationValues = destination.streamPointValues(point, null, null, null, TimeOrder.ASCENDING)
                .collect(Collectors.toList());

        assertEquals(sourceValues.size(), destinationValues.size());
        for (int i = 0; i < sourceValues.size(); i++) {
            var sourceValue = sourceValues.get(i);
            var destinationValue = destinationValues.get(i);
            assertEquals(point.getSeriesId(), destinationValue.getSeriesId());
            assertEquals(sourceValue.getTime(), destinationValue.getTime());
            assertEquals(sourceValue.getValue(), destinationValue.getValue());
        }
    }

    @Test
    public void generatedBrownian() throws ExecutionException, InterruptedException, TimeoutException {
        var dataSource = createMockDataSource();
        var point = createMockDataPoint(dataSource, new MockPointLocatorVO());

        Duration testDuration = Duration.ofDays(30);
        Instant from = Instant.ofEpochMilli(0L);
        Instant to = from.plus(testDuration);
        Duration period = Duration.ofHours(1L);
        long expectedSamples = testDuration.toMillis() / period.toMillis();

        // migration stops at the current time
        timer.setStartTime(to.toEpochMilli());

        BrownianPointValueGenerator generator = new BrownianPointValueGenerator(from.toEpochMilli(), to.toEpochMilli(), period.toMillis());
        source.savePointValues(generator.apply(point));
        // sanity check
        assertEquals(expectedSamples, source.dateRangeCount(point, null, null));

        migrationPointValueDao.startMigration();
        migrationPointValueDao.migrationFinished().get(30, TimeUnit.SECONDS);

        var destinationValues = destination.streamPointValues(point, null, null, null, TimeOrder.ASCENDING)
                .collect(Collectors.toList());

        assertEquals(expectedSamples, destinationValues.size());
        for (int i = 0; i < expectedSamples; i++) {
            var destinationValue = destinationValues.get(i);
            assertEquals(point.getSeriesId(), destinationValue.getSeriesId());
            assertEquals(from.plus(period.multipliedBy(i)).toEpochMilli(), destinationValue.getTime());
        }
    }

    @Test
    public void multipleDataPoints() throws ExecutionException, InterruptedException, TimeoutException {
        var dataSource = createMockDataSource();
        var points = createMockDataPoints(dataSource, 100);

        Period testDuration = Period.ofMonths(6);
        ZonedDateTime from = ZonedDateTime.of(LocalDateTime.of(2020, 1, 1, 0, 0), ZoneOffset.UTC);
        ZonedDateTime to = from.plus(testDuration);
        Duration period = Duration.ofHours(1L);
        long expectedSamples = Duration.between(from, to).dividedBy(period);

        // migration stops at the current time
        timer.setStartTime(to.toInstant().toEpochMilli());

        BrownianPointValueGenerator generator = new BrownianPointValueGenerator(from.toInstant().toEpochMilli(), to.toInstant().toEpochMilli(), period.toMillis());
        for (var point : points) {
            source.savePointValues(generator.apply(point));
            // sanity check
            assertEquals(expectedSamples, source.dateRangeCount(point, null, null));
        }

        migrationPointValueDao.startMigration();
        migrationPointValueDao.migrationFinished().get(30, TimeUnit.SECONDS);

        for (var point : points) {
            var destinationValues = destination.streamPointValues(point, null, null, null, TimeOrder.ASCENDING)
                    .collect(Collectors.toList());

            assertEquals(expectedSamples, destinationValues.size());
            for (int i = 0; i < expectedSamples; i++) {
                var destinationValue = destinationValues.get(i);
                assertEquals(point.getSeriesId(), destinationValue.getSeriesId());
                assertEquals(from.plus(period.multipliedBy(i)).toInstant().toEpochMilli(), destinationValue.getTime());
            }
        }
    }

    @Override
    protected MockMangoLifecycle getLifecycle() {
        MockMangoLifecycle lifecycle = super.getLifecycle();
        lifecycle.addRuntimeContextConfiguration(MigrationSpringConfig.class);
        return lifecycle;
    }

    private static class MigrationSpringConfig {

        @Bean
        @Primary
        public MigrationConfig migrationConfig() {
            return new TestMigrationConfig();
        }

        @Bean("source")
        public PointValueDao source() {
            return new MockPointValueDao();
        }

        @Bean("destination")
        public PointValueDao destination() {
            return new MockPointValueDao();
        }

        @Bean
        @Primary
        public PointValueDao pointValueDao(@Qualifier("source") PointValueDao source,
                                           @Qualifier("destination") PointValueDao destination,
                                           DataPointDao dataPointDao,
                                           AbstractTimer timer,
                                           MigrationProgressDao migrationProgressDao,
                                           MigrationConfig config,
                                           ExecutorService executor,
                                           ScheduledExecutorService scheduledExecutor) {

            return new MigrationPointValueDao(destination, source, dataPointDao,
                    executor, scheduledExecutor, timer, migrationProgressDao, config);
        }
    }
}
