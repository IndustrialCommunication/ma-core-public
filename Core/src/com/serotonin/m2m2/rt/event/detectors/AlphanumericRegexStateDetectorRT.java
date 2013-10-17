/* 
	Copyright (C) 2013 Infinite Automation. All rights reserved.
	@author Phillip Dunlap
*/
package com.serotonin.m2m2.rt.event.detectors;

import java.util.regex.Pattern;

import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.view.text.TextRenderer;
import com.serotonin.m2m2.vo.event.PointEventDetectorVO;

public class AlphanumericRegexStateDetectorRT extends StateDetectorRT { 
    public AlphanumericRegexStateDetectorRT(PointEventDetectorVO vo) {
        this.vo = vo;
    }

    @Override
    public TranslatableMessage getMessage() {
        String name = vo.njbGetDataPoint().getName();
        String prettyText = vo.njbGetDataPoint().getTextRenderer()
                .getText(vo.getAlphanumericState(), TextRenderer.HINT_SPECIFIC);
        TranslatableMessage durationDescription = getDurationDescription();

        if (durationDescription != null)
            return new TranslatableMessage("event.detector.periodState", name, prettyText, durationDescription);
        return new TranslatableMessage("event.detector.state", name, prettyText);
    }

    @Override
    protected boolean stateDetected(PointValueTime newValue) {
        String newAlpha = newValue.getStringValue();
        if(newAlpha != null)
        	return Pattern.compile(vo.getAlphanumericState()).matcher(newAlpha).find();
        return false;
    }
}