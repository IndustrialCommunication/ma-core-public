/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.event.detector;

import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.detectors.AbstractEventDetectorRT;
import com.serotonin.m2m2.rt.event.detectors.NoChangeDetectorRT;
import com.serotonin.m2m2.vo.DataPointVO;

/**
 * @author Terry Packer
 *
 */
public class NoChangeDetectorVO extends TimeoutDetectorVO<NoChangeDetectorVO>{

	private static final long serialVersionUID = 1L;
	
	public NoChangeDetectorVO(DataPointVO vo) {
		super(vo, new int[] { DataTypes.BINARY, DataTypes.MULTISTATE, DataTypes.NUMERIC, DataTypes.ALPHANUMERIC });
		this.setDuration(1);
	}
	
	@Override
	public AbstractEventDetectorRT<NoChangeDetectorVO> createRuntime() {
		return new NoChangeDetectorRT(this);
	}

	@Override
	protected TranslatableMessage getConfigurationDescription() {
		return new TranslatableMessage("event.detectorVo.noChange", getDurationDescription());
	}

}
