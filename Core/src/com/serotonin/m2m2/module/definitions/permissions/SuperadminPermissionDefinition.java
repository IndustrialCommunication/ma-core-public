/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.module.definitions.permissions;

import java.util.Collections;
import java.util.Set;

import com.infiniteautomation.mango.permission.MangoPermission;
import com.serotonin.m2m2.db.dao.RoleDao;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;

/**
 * @author Terry Packer
 *
 */
public class SuperadminPermissionDefinition extends PermissionDefinition {
    public static final String GROUP_NAME = "superadmin";
    public static final String PERMISSION = "permissions.superadmin";
    
    public SuperadminPermissionDefinition() {
    
    }

    @Override
    public TranslatableMessage getDescription() {
        return new TranslatableMessage("systemSettings.permissions.superadmin");
    }

    @Override
    public String getPermissionTypeName() {
        return PERMISSION;
    }

    @Override
    public Set<Role> getDefaultRoles() {
        return Collections.singleton(PermissionHolder.SUPERADMIN_ROLE.get());
    }

    @Override
    public MangoPermission getPermission() {
        return new MangoPermission(PERMISSION, Collections.singleton(RoleDao.getInstance().getByXid(PermissionHolder.SUPERADMIN_ROLE_XID, true)));
    }
}
