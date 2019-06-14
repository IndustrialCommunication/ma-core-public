/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 *
 */
package com.serotonin.m2m2.module.definitions.permissions;

import com.serotonin.m2m2.module.PermissionDefinition;

/**
 *
 * @author Terry Packer
 */
public class SqlRestoreActionPermissionDefinition extends PermissionDefinition{

    public static final String PERMISSION = "action.sqlRestore";

    @Override
    public String getPermissionKey() {
        return "systemSettings.restoreDatabase";
    }

    @Override
    public String getPermissionTypeName() {
        return PERMISSION;
    }

}
