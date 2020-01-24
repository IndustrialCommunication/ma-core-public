/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.event.detectors;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.commons.lang3.StringUtils;

import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.detector.AlphanumericRegexStateDetectorVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
public class AlphanumericRegexStateEventDetectorDefinition extends TimeoutDetectorDefinition<AlphanumericRegexStateDetectorVO>{

	public static final String TYPE_NAME = "ALPHANUMERIC_REGEX_STATE";
		

	@Override
	public String getEventDetectorTypeName() {
		return TYPE_NAME;
	}

	@Override
	public String getDescriptionKey() {
		return "pointEdit.detectors.regexState";
	}

	@Override
	protected AlphanumericRegexStateDetectorVO createEventDetectorVO(DataPointVO dp) {
		return new AlphanumericRegexStateDetectorVO(dp);
	}
	
	@Override
	protected AlphanumericRegexStateDetectorVO createEventDetectorVO(int sourceId) {
	    return new AlphanumericRegexStateDetectorVO(DataPointDao.getInstance().get(sourceId));
	}
	
    @Override
    public void validate(ProcessResult response, AlphanumericRegexStateDetectorVO vo, PermissionHolder user) {
        super.validate(response, vo, user);
        
        if(StringUtils.isEmpty(vo.getState()))
            response.addContextualMessage("state", "validate.cannotContainEmptyString");
        try {
            Pattern.compile(vo.getState());
        } catch(PatternSyntaxException e) {
            response.addContextualMessage("state", "validate.invalidRegex", e.getMessage());
        }
    }	
	
	
}
