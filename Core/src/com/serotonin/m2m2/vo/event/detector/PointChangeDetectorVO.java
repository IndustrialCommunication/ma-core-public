/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.event.detector;

import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.detectors.AbstractEventDetectorRT;
import com.serotonin.m2m2.rt.event.detectors.PointChangeDetectorRT;
import com.serotonin.m2m2.vo.DataPointVO;

/**
 * @author Terry Packer
 *
 */
public class PointChangeDetectorVO extends AbstractPointEventDetectorVO<PointChangeDetectorVO>{

	private static final long serialVersionUID = 1L;
	
	public PointChangeDetectorVO(DataPointVO vo) {
		super(vo, new int[] {
                DataTypes.BINARY,
                DataTypes.MULTISTATE,
                DataTypes.NUMERIC,
                DataTypes.ALPHANUMERIC });
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.vo.event.detector.AbstractEventDetectorVO#createRuntime()
	 */
	@Override
	public AbstractEventDetectorRT<PointChangeDetectorVO> createRuntime() {
		return new PointChangeDetectorRT(this);
	}

	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.vo.event.detector.AbstractEventDetectorVO#getConfigurationDescription()
	 */
	@Override
	protected TranslatableMessage getConfigurationDescription() {
		return new TranslatableMessage("event.detectorVo.change");
	}
	
	@Override
    public boolean isRtnApplicable() {
    	return false;
    }

}
