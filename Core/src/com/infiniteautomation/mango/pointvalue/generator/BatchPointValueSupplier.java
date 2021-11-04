/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.infiniteautomation.mango.pointvalue.generator;

import java.util.function.Supplier;
import java.util.stream.Stream;

import com.serotonin.m2m2.db.dao.BatchPointValue;

public interface BatchPointValueSupplier extends Supplier<BatchPointValue> {

    long getTimestamp();
    void setTimestamp(long timestamp);

    default Stream<BatchPointValue> stream() {
        return Stream.generate(this);
    }
}