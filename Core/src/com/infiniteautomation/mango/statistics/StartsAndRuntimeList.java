/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.infiniteautomation.mango.statistics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.serotonin.ShouldNeverHappenException;
import com.serotonin.m2m2.rt.dataImage.types.DataValue;
import com.serotonin.m2m2.view.stats.IValueTime;
import com.serotonin.m2m2.view.stats.StatisticsGenerator;

/**
 * Track runtime, state changes and percentage in state of total runtime (not period)
 * 
 * @author Matthew Lohbihler, Terry Packer
 */
public class StartsAndRuntimeList implements StatisticsGenerator {
    // Configuration values.
    private final long periodStart;
    private final long periodEnd;
    private boolean done = false;

    // Calculated values.
    private DataValue startValue;
    private DataValue firstValue;
    private Long firstTime;
    private DataValue lastValue;
    private Long lastTime;
    private final List<StartsAndRuntime> data = new ArrayList<StartsAndRuntime>();
    private int count;

    // State values.
    private long latestTime;
    private StartsAndRuntime sar;

    public StartsAndRuntimeList(long periodStart, long periodEnd, IValueTime startVT,
            List<? extends IValueTime> values) {
      this(periodStart, periodEnd, startVT);
      for (IValueTime vt : values)
          addValueTime(vt);
      done();
    }

    public StartsAndRuntimeList(long periodStart, long periodEnd, IValueTime startValue) {
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;

        //Check for null and also bookend values
        if (startValue != null && startValue.getValue() != null) {
            this.startValue = startValue.getValue();
            latestTime = periodStart;
            sar = get(this.startValue);
        }
    }

    @Override
    public void addValueTime(IValueTime vt) {
        addValueTime(vt.getValue(), vt.getTime());
    }

    public void addValueTime(DataValue value, long time) {
        if (value == null)
            return;

        count++;

        if (firstValue == null) {
            firstValue = value;
            firstTime = time;
        }

        if (sar != null)
            sar.runtime += time - latestTime;

        latestTime = time;
        sar = get(value);
        sar.starts++;
        lastValue = value;
        lastTime = time;
    }

    @Override
    public void done() {
        if(done)
            throw new ShouldNeverHappenException("Should not call done() more than once.");
        done = true;
        
        // If there is a current SAR, update 
        // if (endValue != null && sar != null)
        if (sar != null)
            sar.runtime += periodEnd - latestTime;

        // Calculate the total duration as the sum of the runtimes.
        long totalRuntime = 0;
        for (StartsAndRuntime s : data)
            totalRuntime += s.runtime;

        // Calculate runtime percentages.
        for (StartsAndRuntime s : data)
            s.calculateRuntimePercentage(totalRuntime);

        // Sort by value.
        Collections.sort(data, new Comparator<StartsAndRuntime>() {
            @Override
            public int compare(StartsAndRuntime o1, StartsAndRuntime o2) {
                return o1.value.compareTo(o2.value);
            }
        });
    }

    @Override
    public long getPeriodStartTime() {
        return periodStart;
    }

    @Override
    public long getPeriodEndTime() {
        return periodEnd;
    }
    
    public DataValue getStartValue() {
        return startValue;
    }
    
    public DataValue getFirstValue() {
        return firstValue;
    }

    public Long getFirstTime() {
        return firstTime;
    }

    public DataValue getLastValue() {
        return lastValue;
    }

    public Long getLastTime() {
        return lastTime;
    }

    public int getCount(){
        return count;
    }
    
    public Map<Object, StartsAndRuntime> getStartsAndRuntime() {
        Map<Object, StartsAndRuntime> result = new HashMap<Object, StartsAndRuntime>();
        for (StartsAndRuntime sar : data)
            result.put(sar.getValue(), sar);
        return result;
    }

    public List<StartsAndRuntime> getData() {
        return data;
    }

    private StartsAndRuntime get(DataValue value) {
        for (StartsAndRuntime sar : data) {
            if (Objects.equals(sar.value, value))
                return sar;
        }

        StartsAndRuntime sar = new StartsAndRuntime();
        sar.value = value;
        data.add(sar);

        return sar;
    }

    public String getHelp() {
        return toString();
    }

    @Override
    public String toString() {
        return "{data: " + data.toString() +
        		", periodStartTime: " + periodStart + 
        		", periodEndTime: " + periodEnd + 
        		", count: " + count +
        		", startValue: " + startValue +
        		", firstValue: " + firstValue +
        		", firstTime: " + firstTime + 
        		", lastValue: " + lastValue +
        		", lastTime: " + lastTime + 
        		"}";
    }
}
