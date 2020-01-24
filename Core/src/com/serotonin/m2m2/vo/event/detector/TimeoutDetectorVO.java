/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.event.detector;

import java.io.IOException;

import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.type.JsonObject;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableJsonException;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.vo.DataPointVO;

/**
 * @author Terry Packer
 *
 */
public abstract class TimeoutDetectorVO<T extends AbstractPointEventDetectorVO<T>> extends AbstractPointEventDetectorVO<T> {

	/**
	 * @param supportedDataTypes
	 */
	public TimeoutDetectorVO(DataPointVO vo, int[] supportedDataTypes) {
		super(vo, supportedDataTypes);
	}

	private static final long serialVersionUID = 1L;
	
	protected int duration;
    protected int durationType = Common.TimePeriods.SECONDS;
    
    public int getDuration() {
		return duration;
	}
	public void setDuration(int duration) {
		this.duration = duration;
	}
	public int getDurationType() {
		return durationType;
	}
	public void setDurationType(int durationType) {
		this.durationType = durationType;
	}
	
	public TranslatableMessage getDurationDescription() {
        if (duration == 0)
            return null;
        return Common.getPeriodDescription(durationType, duration);
    }
	
    @Override
    public void jsonWrite(ObjectWriter writer) throws IOException, JsonException {
    	super.jsonWrite(writer);
        writer.writeEntry("durationType", Common.TIME_PERIOD_CODES.getCode(durationType));
        writer.writeEntry("duration", duration);
    }

    @Override
    public void jsonRead(JsonReader reader, JsonObject jsonObject) throws JsonException {
    	super.jsonRead(reader, jsonObject);
        
    	String text = jsonObject.getString("durationType");
        if (text == null)
            throw new TranslatableJsonException("emport.error.ped.missing", "durationType",
                    Common.TIME_PERIOD_CODES.getCodeList());

        durationType = Common.TIME_PERIOD_CODES.getId(text);
        if (!Common.TIME_PERIOD_CODES.isValidId(durationType))
            throw new TranslatableJsonException("emport.error.ped.invalid", "durationType", text,
                    Common.TIME_PERIOD_CODES.getCodeList());

        duration = getInt(jsonObject, "duration");

    }
	
}
