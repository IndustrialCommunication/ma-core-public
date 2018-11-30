/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.vo.event;

import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.AlarmLevels;
import com.serotonin.m2m2.rt.event.type.EventType;

public class EventTypeVO {
    protected final EventType eventType;
    protected final TranslatableMessage description;
    protected final AlarmLevels alarmLevel;

    public EventTypeVO(EventType eventType, TranslatableMessage description, AlarmLevels alarmLevel) {
        this.eventType = eventType;
        this.description = description;
        this.alarmLevel = alarmLevel;
    }

    public EventType getEventType() {
        return eventType;
    }

    public TranslatableMessage getDescription() {
        return description;
    }

    public AlarmLevels getAlarmLevel() {
        return alarmLevel;
    }

    /**
     * Used in DWR and JSP only
     * @return
     */
    @Deprecated
    public String getType() {
        return eventType.getEventType();
    }

    /**
     * Used in DWR and JSP only
     * @return
     */
    @Deprecated
    public String getSubtype() {
        return eventType.getEventSubtype();
    }

    /**
     * Used in DWR and JSP only
     * @return
     */
    @Deprecated
    public int getTypeRef1() {
        return eventType.getReferenceId1();
    }

    /**
     * Used in DWR and JSP only
     * @return
     */
    @Deprecated
    public int getTypeRef2() {
        return eventType.getReferenceId2();
    }

}
