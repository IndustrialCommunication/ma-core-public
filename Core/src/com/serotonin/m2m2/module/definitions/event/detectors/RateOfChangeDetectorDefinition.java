/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.module.definitions.event.detectors;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.detector.RateOfChangeDetectorVO;
import com.serotonin.m2m2.vo.event.detector.RateOfChangeDetectorVO.CalculationMode;
import com.serotonin.m2m2.vo.event.detector.RateOfChangeDetectorVO.ComparisonMode;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
public class RateOfChangeDetectorDefinition extends TimeoutDetectorDefinition<RateOfChangeDetectorVO> {

    public static final String TYPE_NAME = "RATE_OF_CHANGE";
    
    @Override
    protected RateOfChangeDetectorVO createEventDetectorVO(DataPointVO dp) {
        return new RateOfChangeDetectorVO(dp);
    }

    @Override
    public String getEventDetectorTypeName() {
        return TYPE_NAME;
    }

    @Override
    public String getDescriptionKey() {
        return "pointEdit.detectors.rateOfChange";
    }
    
    @Override
    public void validate(ProcessResult response, RateOfChangeDetectorVO vo, PermissionHolder user) {
        super.validate(response, vo, user);

        if(vo.getComparisonMode() == null) {
            response.addContextualMessage("comparisonMode", "validate.required");
            return;
        }
        
        if(vo.getCalculationMode() == null) {
            response.addContextualMessage("calculationMode", "validate.required");
            return;
        }
        
        if(vo.getCalculationMode() == CalculationMode.AVERAGE && vo.getRateOfChangePeriods() == 0)
            response.addContextualMessage("rateOfChangePeriods", "validate.greaterThanZero");
        else if(vo.getCalculationMode() == CalculationMode.INSTANTANEOUS && vo.getRateOfChangePeriods() != 0)
            response.addContextualMessage("rateOfChangePeriods", "validate.invalidValue");
            
        
        if(vo.isUseResetThreshold()) {
            if(vo.getComparisonMode() == ComparisonMode.LESS_THAN && vo.getResetThreshold() <= vo.getRateOfChangeThreshold()) {
                response.addContextualMessage("resetThreshold", "validate.greaterThan", vo.getRateOfChangeThreshold());
            } else if(vo.getComparisonMode() == ComparisonMode.LESS_THAN_OR_EQUALS && vo.getResetThreshold() <= vo.getRateOfChangeThreshold()) {
                response.addContextualMessage("resetThreshold", "validate.greaterThan", vo.getRateOfChangeThreshold());
            } else if(vo.getComparisonMode() == ComparisonMode.GREATER_THAN && vo.getResetThreshold() >= vo.getRateOfChangeThreshold()) {
                response.addContextualMessage("resetThreshold", "validate.lessThan", vo.getRateOfChangeThreshold());
            } else if(vo.getComparisonMode() == ComparisonMode.GREATER_THAN_OR_EQUALS && vo.getResetThreshold() >= vo.getRateOfChangeThreshold()) {
                response.addContextualMessage("resetThreshold", "validate.lessThan", vo.getRateOfChangeThreshold());
            }
        }
        
        if (!Common.TIME_PERIOD_CODES.isValidId(vo.getRateOfChangeThresholdPeriodType()))
            response.addContextualMessage("rateOfChangeThresholdPeriodType", "validate.invalidValue");
        
        if (!Common.TIME_PERIOD_CODES.isValidId(vo.getRateOfChangePeriodType()))
            response.addContextualMessage("rateOfChangePeriodType", "validate.invalidValue");
    }

}
