/**
 * Copyright (C) 2018  Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.rt.event.handlers;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.serotonin.m2m2.MangoTestBase;
import com.serotonin.m2m2.MockBackgroundProcessing;
import com.serotonin.m2m2.MockMangoLifecycle;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.AlarmLevels;
import com.serotonin.m2m2.rt.event.EventInstance;
import com.serotonin.m2m2.rt.event.type.DataPointEventType;
import com.serotonin.m2m2.rt.maint.BackgroundProcessing;
import com.serotonin.m2m2.rt.maint.work.EmailWorkItem;
import com.serotonin.m2m2.rt.maint.work.WorkItem;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.event.EmailEventHandlerVO;
import com.serotonin.m2m2.vo.event.detector.AnalogChangeDetectorVO;
import com.serotonin.m2m2.vo.mailingList.EmailRecipient;
import com.serotonin.m2m2.web.dwr.beans.RecipientListEntryBean;

/**
 * @author Terry Packer
 *
 */
public class EmailHandlerRTTest extends MangoTestBase {
    
    //Scheduled Work Items for Emails
    private static final List<WorkItem> scheduledItems = new ArrayList<>();

    
    @Before
    public void beforeEmailHandlerTests() {
        scheduledItems.clear();
    }
    
    @After
    @Override
    public void after() {
        
    }
    
    @Test
    public void testSendActive() {
        
        EmailEventHandlerVO vo = createVO();
        List<RecipientListEntryBean> activeRecipients = createRecipients();
        vo.setActiveRecipients(activeRecipients);
        EmailHandlerRT rt = new EmailHandlerRT(vo);
        EventInstance evt = createDataPointEventInstance();
        rt.eventRaised(evt);
        
        //Ensure there is one scheduled
        assertEquals(1, scheduledItems.size());
        scheduledItems.clear();
        
        //Make Inactive
        rt.eventInactive(evt);
        assertEquals(0, scheduledItems.size());

    }
    
    @Test
    public void testSendActiveInactive() {
        
        EmailEventHandlerVO vo = createVO();
        vo.setSendInactive(true);
        List<RecipientListEntryBean> activeRecipients = createRecipients();
        vo.setActiveRecipients(activeRecipients);
        EmailHandlerRT rt = new EmailHandlerRT(vo);
        EventInstance evt = createDataPointEventInstance();
        rt.eventRaised(evt);
        
        //Ensure there is one scheduled
        assertEquals(1, scheduledItems.size());
        scheduledItems.clear();
        
        //Make Inactive
        rt.eventInactive(evt);
        assertEquals(1, scheduledItems.size());

    }
    
    @Test
    public void testSendNoActive() {
        
        EmailEventHandlerVO vo = createVO();
        EmailHandlerRT rt = new EmailHandlerRT(vo);
        EventInstance evt = createDataPointEventInstance();
        rt.eventRaised(evt);
        
        //Ensure there are no work items scheduled for emails.
        assertEquals(0, scheduledItems.size());
        scheduledItems.clear();
        
        //Make Inactive
        rt.eventInactive(evt);
        assertEquals(0, scheduledItems.size());
        
    }
    
    @Test
    public void testSendInactive() {
        
        EmailEventHandlerVO vo = createVO();
        List<RecipientListEntryBean> activeRecipients = createRecipients();
        vo.setActiveRecipients(activeRecipients);
        
        vo.setSendInactive(true);
        List<RecipientListEntryBean> inactiveRecipients = createRecipients();
        vo.setInactiveRecipients(inactiveRecipients);
        
        EmailHandlerRT rt = new EmailHandlerRT(vo);
        EventInstance evt = createDataPointEventInstance();
        rt.eventRaised(evt);
        
        //Ensure there is one scheduled
        assertEquals(1, scheduledItems.size());
        scheduledItems.clear();
        
        //Make Inactive
        rt.eventInactive(evt);
        assertEquals(1, scheduledItems.size());
    }
    
    protected EventInstance createDataPointEventInstance() {

        DataPointEventType type = new DataPointEventType(1, 1);
        
        Map<String,Object> context = new HashMap<String, Object>();
        AnalogChangeDetectorVO ped = new AnalogChangeDetectorVO();
        context.put("pointEventDetector", ped);
        DataPointVO dp = new DataPointVO();
        dp.setDataSourceName("test data source");
        dp.setTags(new HashMap<>());
        
        context.put("point", dp);
        
        EventInstance instance = new EventInstance(
                type, 
                this.timer.currentTimeMillis() ,
                true, 
                AlarmLevels.CRITICAL,
                new TranslatableMessage("common.default", "testing"),
                context);
        
        return instance;
    }
    protected EmailEventHandlerVO createVO() {
        EmailEventHandlerVO vo = new EmailEventHandlerVO();
        vo.setAlias("Alias");
        
        return vo;
    }
    
    protected List<RecipientListEntryBean> createRecipients() {
        List<RecipientListEntryBean> recipients = new ArrayList<>();
        RecipientListEntryBean address = new RecipientListEntryBean();
        address.setRecipientType(EmailRecipient.TYPE_ADDRESS);
        address.setReferenceAddress("test@test.com");
        recipients.add(address);
        return recipients;
    }
    
    @Override
    protected MockMangoLifecycle getLifecycle() {
        return mockLifecycle;
    }
    
    private final MockMangoLifecycle mockLifecycle = new MockMangoLifecycle(modules, enableH2Web, h2WebPort) {
        @Override
        protected BackgroundProcessing getBackgroundProcessing() {
            return new MockBackgroundProcessing() {
                @Override
                public void addWorkItem(WorkItem item) {
                    if(item instanceof EmailWorkItem) {
                        scheduledItems.add(item);
                    }else {
                        super.addWorkItem(item);
                    }
                }
            };
        }
    };
}
