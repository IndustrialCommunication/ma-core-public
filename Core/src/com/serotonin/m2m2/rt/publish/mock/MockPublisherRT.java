/**
 * Copyright (C) 2020  Infinite Automation Software. All rights reserved.
 */

package com.serotonin.m2m2.rt.publish.mock;

import com.serotonin.m2m2.rt.publish.PublisherRT;
import com.serotonin.m2m2.vo.publish.PublisherVO;
import com.serotonin.m2m2.vo.publish.mock.MockPublishedPointVO;

/**
 *
 * @author Terry Packer
 */
public class MockPublisherRT extends PublisherRT<MockPublishedPointVO> {

    public MockPublisherRT(PublisherVO<MockPublishedPointVO> vo) {
        super(vo);
    }

    @Override
    public void initialize() {

    }

}
