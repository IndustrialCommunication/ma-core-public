/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 *
 */
package com.infiniteautomation.mango.scheduling.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.serotonin.m2m2.i18n.ProcessResult;

/**
 *
 * Schedule for 7 Days.
 *   Contains a list of schedules for each day of the week, 
 *   first entry is Sunday.
 *   
 * @author Terry Packer
 */
public class WeeklySchedule {
    
    List<DailySchedule> dailySchedules;
    
    public WeeklySchedule() {
        this(new ArrayList<>());
    }
    
    @JsonCreator
    public WeeklySchedule(List<DailySchedule> dailySchedules) {
        this.dailySchedules = dailySchedules;
    }
    
    /**
     * @return the dailySchedules
     */
    @JsonValue
    public List<DailySchedule> getDailySchedules() {
        return dailySchedules;
    }

    /**
     * @param dailySchedules the dailySchedules to set
     */
    public void setDailySchedules(List<DailySchedule> dailySchedules) {
        this.dailySchedules = dailySchedules;
    }

    /**
     * @param day
     */
    public void addDay(DailySchedule day) {
        this.dailySchedules.add(day);
    }

    /**
     * @param response
     */
    public void validate(ProcessResult response) {
        for(DailySchedule schedule : dailySchedules) {
            schedule.validate(response);
        }
    }

    /**
     * @return
     */
    public int getOffsetCount() {
        int count = 0;
        for(DailySchedule schedule : dailySchedules) {
            count += schedule.getChanges().size();
        }
        return count;
    }
}
