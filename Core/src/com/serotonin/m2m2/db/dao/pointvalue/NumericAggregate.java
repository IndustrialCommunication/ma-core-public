/*
 * Copyright (C) 2022 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.dao.pointvalue;

/**
 * @author Jared Wiltshire
 */
public interface NumericAggregate extends AggregateValue {

    Double getMinimumValue();

    Long getMinimumTime();

    Double getMaximumValue();

    Long getMaximumTime();

    Double getAverage();

    Double getIntegral();

    double getSum();

    double getDelta();

    double getArithmeticMean();

    double getMinimumInPeriod();

    double getMaximumInPeriod();
}
