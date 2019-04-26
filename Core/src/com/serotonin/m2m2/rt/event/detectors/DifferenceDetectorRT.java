/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.rt.event.detectors;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.vo.event.detector.TimeoutDetectorVO;

/**
 * @author Matthew Lohbihler, Terry Packer
 */
abstract public class DifferenceDetectorRT<T extends TimeoutDetectorVO<T>> extends TimeDelayedEventDetectorRT<T> {

	public DifferenceDetectorRT(T vo) {
		super(vo);
	}

	/**
     * State field. Whether the event is currently active or not. This field is used to prevent multiple events being
     * raised during the duration of a single state detection.
     */
    protected boolean eventActive;

    /**
     * Time which we last changed value
     */
    protected long lastChange;

    @Override
	public boolean isEventActive() {
        return eventActive;
    }

    /**
     * Received point data
     * @param fireTime
     */
    synchronized protected void pointData(long fireTime) {
        if (!eventActive)
            unscheduleJob(fireTime);
        else
            setEventInactive(fireTime);
        lastChange = fireTime;      
        scheduleJob(fireTime);
    }

    @Override
    public void initializeState() {
        long now = Common.timer.currentTimeMillis();
        // Get historical data for the point out of the database.
        int pointId = vo.getDataPoint().getId();
        PointValueTime latest = Common.runtimeManager.getDataPoint(pointId).getPointValue();
        if (latest != null)
            lastChange = latest.getTime();
        else
            // The point may be new or not logged, so don't go active immediately.
            lastChange = now;

        if (lastChange + getDurationMS() < now)
            // Nothing has happened in the time frame, so set the event active.
            setEventActive(lastChange + getDurationMS());
        else
            // Otherwise, set the timeout.
            scheduleJob(now);
    }

    @Override
    protected long getConditionActiveTime() {
        return lastChange;
    }
    
    @Override
    public void scheduleTimeoutImpl(long fireTime) {
        //Ensure that the pointData() method hasn't updated our last change time and that we are not active already 
        //TODO I don't think we need !eventActive here
        if(lastChange + getDurationMS() < fireTime && !eventActive)
            setEventActive(fireTime);
    }
    
    @Override
    public synchronized void setEventActive(long timestamp) {
        eventActive = true;
        raiseEvent(timestamp, createEventContext());
    }
    
    @Override
    protected void setEventInactive(long timestamp) {
        eventActive = false;
        returnToNormal(timestamp);
    }
}
