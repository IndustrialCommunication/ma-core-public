/*
 * Copyright (C) 2022 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.dao.migration;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.TemporalAmount;
import java.time.temporal.TemporalUnit;
import java.util.function.Predicate;

import com.serotonin.m2m2.vo.DataPointVO;

/**
 * @author Jared Wiltshire
 */
public interface MigrationConfig {
    Instant getMigrateFromTime();
    Duration getMigrationPeriod();
    int getMaxAttempts();
    boolean isAutoStart();
    boolean isStartNewMigration();
    int getLogPeriodSeconds();
    int getReadChunkSize();
    int getWriteChunkSize();
    int getThreadCount();
    Duration getCloseWait();
    Predicate<DataPointVO> getDataPointFilter();
    ZoneId getZone();
    TemporalAmount getAggregationPeriod();
    TemporalAmount getAggregationDelay();
    TemporalUnit getTruncateTo();
}
