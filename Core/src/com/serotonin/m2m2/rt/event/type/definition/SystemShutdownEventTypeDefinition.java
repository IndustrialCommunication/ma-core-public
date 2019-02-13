/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.rt.event.type.definition;

import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.module.SystemEventTypeDefinition;
import com.serotonin.m2m2.rt.event.type.SystemEventType;

/**
 * @author Terry Packer
 *
 */
public class SystemShutdownEventTypeDefinition extends SystemEventTypeDefinition {

    @Override
    public String getTypeName() {
       return SystemEventType.TYPE_SYSTEM_SHUTDOWN;
    }

    @Override
    public String getDescriptionKey() {
        return "event.system.shutdown";
    }

    @Override
    public String getEventListLink(int ref1, int ref2, Translations translations) {
        return null;
    }

    @Override
    public boolean supportsReferenceId1() {
        return false;
    }

    @Override
    public boolean supportsReferenceId2() {
        return false;
    }

}
