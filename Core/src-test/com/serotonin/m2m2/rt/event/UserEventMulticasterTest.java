/**
 * Copyright (C) 2018 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.rt.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.spring.service.RoleService;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.MangoTestBase;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.type.DuplicateHandling;
import com.serotonin.m2m2.rt.event.type.MockEventType;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.RoleVO;

/**
 * Test the logic of the multicaster
 * @author Terry Packer
 */
public class UserEventMulticasterTest extends MangoTestBase {

    protected RoleService roleService;
    
    protected PermissionHolder systemSuperadmin;
    protected RoleVO mockRole;
    
    @Before
    public void setupRoles() {
        roleService = Common.getBean(RoleService.class);
        
        systemSuperadmin = PermissionHolder.SYSTEM_SUPERADMIN;

        //Add some roles
        mockRole = new RoleVO(Common.NEW_ID, "MOCK", "Mock test role.");
        roleService.insert(mockRole, false, systemSuperadmin);
        
    }
    
    @Test
    public void testAddRemoveListeners() {
        int userCount = 3;
        List<User> users = createUsers(userCount, PermissionHolder.SUPERADMIN_ROLE.get());
        List<MockUserEventListener> listenerSet1 = new ArrayList<>();
        UserEventListener multicaster = null;
        
        //Add 3 listeners
        for(User u : users) {
            MockUserEventListener l = new MockUserEventListener(u);
            listenerSet1.add(l);
            multicaster = UserEventMulticaster.add(multicaster, l);
        }
        
        //Ensure 3 listeners
        assertEquals(3, UserEventMulticaster.getListenerCount(multicaster));
        
        //Remove 3 listeners
        for(MockUserEventListener l : listenerSet1)
            multicaster = UserEventMulticaster.remove(multicaster, l);
        
        //Ensure 0 listeners
        assertNull(multicaster);
                
        //Add 3 listeners
        List<MockUserEventListener> listenerSet2 = new ArrayList<>();
        for(User u : users) {
            MockUserEventListener l = new MockUserEventListener(u);
            listenerSet2.add(l);
            multicaster = UserEventMulticaster.add(multicaster, l);
        }
        
        //Confirm 3 listeners
        assertEquals(3, UserEventMulticaster.getListenerCount(multicaster));
    }
    
    @Test
    public void testMulticastOddNumberEventsForUser() {
        
        int dataPointId = 1;
        int eventCount = 13;
        int userCount = 13;
        
        List<User> users = createUsers(userCount, PermissionHolder.SUPERADMIN_ROLE.get());
        List<MockUserEventListener> listeners = new ArrayList<>();
        UserEventListener multicaster = null;
        for(User u : users) {
            MockUserEventListener l = new MockUserEventListener(u);
            listeners.add(l);
            multicaster = UserEventMulticaster.add(multicaster, l);
        }
        

        List<EventInstance> events = new ArrayList<>();
        long time = 0;
        for(int i=0; i<eventCount; i++) {
            EventInstance event = createMockEventInstance(i, dataPointId, time);
            events.add(event);
            multicaster.raised(event);
            time += 1;
        }

        //Ack
        for(EventInstance e : events)
            multicaster.acknowledged(e);
        
        //Rtn
        for(EventInstance e : events)
            multicaster.returnToNormal(e);
        
        //Confirm all 100 saw 10000 were raised
        for(MockUserEventListener l : listeners)
            assertEquals(eventCount, l.getRaised().size());
        
        //Confirm all 100 saw 10000 were acked
        for(MockUserEventListener l : listeners)
            assertEquals(eventCount, l.getAcknowledged().size());
        
        //Confirm all 100 saw 10000 were rtned
        for(MockUserEventListener l : listeners)
            assertEquals(eventCount, l.getReturned().size());
    }
    
    @Test
    public void testMulticastEventsForUser() {
        
        int dataPointId = 1;
        int eventCount = 10000;
        int userCount = 100;
        
        List<User> users = createUsers(userCount, PermissionHolder.SUPERADMIN_ROLE.get());
        List<MockUserEventListener> listeners = new ArrayList<>();
        UserEventListener multicaster = null;
        for(User u : users) {
            MockUserEventListener l = new MockUserEventListener(u);
            listeners.add(l);
            multicaster = UserEventMulticaster.add(multicaster, l);
        }
        

        List<EventInstance> events = new ArrayList<>();
        long time = 0;
        for(int i=0; i<eventCount; i++) {
            EventInstance event = createMockEventInstance(i, dataPointId, time);
            events.add(event);
            multicaster.raised(event);
            time += 1;
        }

        //Ack
        for(EventInstance e : events)
            multicaster.acknowledged(e);
        
        //Rtn
        for(EventInstance e : events)
            multicaster.returnToNormal(e);
        
        //Confirm all 100 saw 10000 were raised
        for(MockUserEventListener l : listeners)
            assertEquals(eventCount, l.getRaised().size());
        
        //Confirm all 100 saw 10000 were acked
        for(MockUserEventListener l : listeners)
            assertEquals(eventCount, l.getAcknowledged().size());
        
        //Confirm all 100 saw 10000 were rtned
        for(MockUserEventListener l : listeners)
            assertEquals(eventCount, l.getReturned().size());
    }
    
    @Test
    public void testMulticastEventsForUsersWithPermissions() {
        
        PermissionService service = Common.getBean(PermissionService.class);
        int dataPointId = 1;
        int eventCount = 10000;
        int userCount = 5*6;
        
        //Add them out of order so the tree is jumbled with permissions hither and yon
        List<User> users = createUsers(userCount/6, PermissionHolder.SUPERADMIN_ROLE.get());
        users.addAll(createUsers(userCount/6, mockRole.getRole()));
        users.addAll(createUsers(userCount/6));
        users.addAll(createUsers(userCount/6, PermissionHolder.SUPERADMIN_ROLE.get()));
        users.addAll(createUsers(userCount/6, mockRole.getRole()));
        users.addAll(createUsers(userCount/6));
        List<Integer> idsToNotify = new ArrayList<>();
        List<MockUserEventListener> listeners = new ArrayList<>();
        UserEventListener multicaster = null;
        MockEventType mockEventType =  new MockEventType(DuplicateHandling.ALLOW, null, 0, dataPointId);
        for(User u : users) {
            MockUserEventListener l = new MockUserEventListener(u);
            if(mockEventType.hasPermission(u, service)) //This work is normally done by the event manager handling the raiseEvent calls
                idsToNotify.add(u.getId());    // through an EventNotifyWorkItem
            listeners.add(l);
            multicaster = UserEventMulticaster.add(multicaster, l);
        }
        

        List<EventInstance> events = new ArrayList<>();
        long time = 0;
        for(int i=0; i<eventCount; i++) {
            EventInstance event = createMockEventInstance(i, dataPointId, time);
            events.add(event);
            event.setIdsToNotify(idsToNotify);
            multicaster.raised(event);
            time += 1;
        }

        //Ack
        for(EventInstance e : events)
            multicaster.acknowledged(e);
        
        //Rtn
        for(EventInstance e : events)
            multicaster.returnToNormal(e);
        
        //Confirm those with permissions saw all 10000 raised
        for(MockUserEventListener l : listeners) {
            if(l.getUser().getRoles().isEmpty()) {
                assertEquals(0, l.getRaised().size());
            }else {
                assertEquals(eventCount, l.getRaised().size());
            }
        }
        
        //Confirm those with permissions saw all 10000 acked
        for(MockUserEventListener l : listeners) {
            if(l.getUser().getRoles().isEmpty()) {
                assertEquals(0, l.getAcknowledged().size());
            }else {
                assertEquals(eventCount, l.getAcknowledged().size());
            }
        }
        
        //Confirm those with permissions saw all 10000 rtned
        for(MockUserEventListener l : listeners) {
            if(l.getUser().getRoles().isEmpty()) {
                assertEquals(0, l.getReturned().size());
            }else {
                assertEquals(eventCount, l.getReturned().size());
            }
        }
    }
    
    public EventInstance createMockEventInstance(int id, int dataPointId, long time) {
        MockEventType type = new MockEventType(DuplicateHandling.ALLOW, null, id, dataPointId);
        return new EventInstance(type, time, true, AlarmLevels.URGENT, 
                new TranslatableMessage("common.default", "Mock Event " + id), null);
    }
}
