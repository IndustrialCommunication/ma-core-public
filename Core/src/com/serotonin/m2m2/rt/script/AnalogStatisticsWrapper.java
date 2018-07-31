/**
 * Copyright (C) 2014 Infinite Automation Software. All rights reserved.
 * 
 * @author Terry Packer
 */
package com.serotonin.m2m2.rt.script;

import com.infiniteautomation.mango.statistics.AnalogStatistics;

/**
 * Wrapper to allow use of Java Primitives from Statistics in JavaScript. This is Required for the
 * Nashorn Javascript Engine because it doesn't automatically convert java.lang.xxx to Javascript
 * Number
 * 
 * @author Terry Packer
 *
 */
public class AnalogStatisticsWrapper {

    private final AnalogStatistics statistics;

    public AnalogStatisticsWrapper(AnalogStatistics statistics) {
        this.statistics = statistics;
    }


    public long getPeriodStartTime() {
        return statistics.getPeriodStartTime();
    }

    public long getPeriodEndTime() {
        return statistics.getPeriodEndTime();
    }

    public Double getMinimumValue() {
        return statistics.getMinimumValue();
    }

    public Long getMinimumTime() {
        return statistics.getMinimumTime();
    }

    public Double getMaximumValue() {
        return statistics.getMaximumValue();
    }

    public Long getMaximumTime() {
        return statistics.getMaximumTime();
    }

    public Double getAverage() {
        return statistics.getAverage();
    }

    public Double getIntegral() {
        return statistics.getIntegral();
    }

    public double getSum() {
        return statistics.getSum();
    }

    public Double getStartValue() {
        return statistics.getStartValue();
    }

    public Double getFirstValue() {
        return statistics.getFirstValue();
    }

    public Long getFirstTime() {
        return statistics.getFirstTime();
    }

    public Double getLastValue() {
        return statistics.getLastValue();
    }

    public Long getLastTime() {
        return statistics.getLastTime();
    }

    public int getCount() {
        return statistics.getCount();
    }

    public double getDelta() {
        return statistics.getDelta();
    }

    public String getHelp() {
        return statistics.toString();
    }

    @Override
    public String toString() {
        return statistics.toString();
    }
}
