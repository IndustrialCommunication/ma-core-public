package com.serotonin.m2m2.rt;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.infiniteautomation.mango.monitor.AtomicIntegerMonitor;
import com.infiniteautomation.mango.monitor.MonitoredValues;

/**
 * @author Jared Wiltshire
 */
public class MonitoredValuesTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @Test
    public void testMonitoredValues() throws Exception {
        MonitoredValues monitoredValues = new MonitoredValues();
        AtomicIntegerMonitor monitor = monitoredValues.create("test.monitor").buildAtomic();

        int count = 1000;

        Future<?> f1 = executor.submit(() -> {
            for (int i = 0; i < count; i++) {
                monitor.addValue(1);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            for (int i = 0; i < count; i++) {
                monitor.addValue(-1);
            }
        });

        f1.get(100, TimeUnit.MILLISECONDS);
        f2.get(100, TimeUnit.MILLISECONDS);

        assertEquals(0, (int) monitor.getValue());
    }

}
