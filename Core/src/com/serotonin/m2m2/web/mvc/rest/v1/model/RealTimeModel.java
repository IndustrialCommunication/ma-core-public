/**
 * Copyright (C) 2014 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.web.mvc.rest.v1.model;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.serotonin.m2m2.rt.dataImage.RealTimeDataPointValue;

/**
 * @author Terry Packer
 *
 */
public class RealTimeModel extends AbstractRestModel<RealTimeDataPointValue>{

	/**
	 * @param data
	 */
	public RealTimeModel(RealTimeDataPointValue data) {
		super(data);
	}

	@JsonGetter("deviceName")
	public String getDeviceName(){
		return this.data.getDeviceName();
	}

	@JsonGetter("name")
	public String getName(){
		return this.data.getPointName();
	}
	
	@JsonGetter("renderedValue")
	public String getRenderedValue(){
		return this.data.getRenderedValue();
	}
	
	@JsonGetter("value")
	public Object getValue(){
		return this.data.getPointValue();
	}
	
	@JsonGetter("type")
	public String getType(){
		return this.data.getPointType();
	}
	
	@JsonGetter("unit")
	public String getUnit(){
		return this.data.getUnit();
	}
	
	@JsonGetter("time")
	public long getTime(){
		return this.data.getTimestamp();
	}
	
	@JsonGetter("status")
	public String getStatus(){
		return this.data.getStatus();
	}
	
	@JsonGetter("path")
	public String getPath(){
		return this.data.getPath();
	}
	@JsonGetter("xid")
	public String getXid(){
		return this.data.getXid();
	}


}
