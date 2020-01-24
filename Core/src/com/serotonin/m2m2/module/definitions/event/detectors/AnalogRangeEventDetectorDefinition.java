/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.event.detectors;

import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.detector.AnalogRangeDetectorVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
public class AnalogRangeEventDetectorDefinition extends TimeoutDetectorDefinition<AnalogRangeDetectorVO>{

	public static final String TYPE_NAME = "RANGE";
		
	@Override
	public String getEventDetectorTypeName() {
		return TYPE_NAME;
	}

	@Override
	public String getDescriptionKey() {
		return "pointEdit.detectors.range";
	}

	protected AnalogRangeDetectorVO createEventDetectorVO(DataPointVO vo) {
		return new AnalogRangeDetectorVO(vo);
	}

	@Override
	protected AnalogRangeDetectorVO createEventDetectorVO(int sourceId) {
        return new AnalogRangeDetectorVO(DataPointDao.getInstance().get(sourceId));
	}
	
    @Override
    public void validate(ProcessResult response, AnalogRangeDetectorVO vo, PermissionHolder user) {
        super.validate(response, vo, user);
        
        if(vo.getHigh() <= vo.getLow())
            response.addContextualMessage("high", "validate.greaterThan", vo.getLow());
    }	
	
}
