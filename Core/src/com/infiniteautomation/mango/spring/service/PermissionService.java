/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.infiniteautomation.mango.permission.MangoPermission;
import com.serotonin.m2m2.db.dao.RoleDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.module.definitions.permissions.DataSourcePermissionDefinition;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.vo.AbstractVO;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.IDataPoint;
import com.serotonin.m2m2.vo.RoleVO;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.event.EventTypeVO;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

/**
 * @author Terry Packer
 *
 */
@Service
public class PermissionService {
    
    //Permission Types
    public static final String READ = "READ";
    public static final String EDIT = "EDIT";
    public static final String DELETE = "DELETE";
    public static final String SET = "SET";
    
    private final RoleDao roleDao;
    private final DataSourcePermissionDefinition dataSourcePermission;
    
    @Autowired
    public PermissionService(RoleDao roleDao) {
        this.roleDao = roleDao;
        this.dataSourcePermission = (DataSourcePermissionDefinition) ModuleRegistry.getPermissionDefinition(DataSourcePermissionDefinition.PERMISSION);
    }
    
    /**
     * Does this user have the superadmin role?
     * @param holder
     * @return
     */
    public boolean hasAdminRole(PermissionHolder holder) {
        return hasSingleRole(holder, roleDao.getSuperadminRole());
    }
    
    /**
     * Ensure this holder has the superadmin role
     * @param holder
     */
    public void ensureAdminRole(PermissionHolder holder) throws PermissionException {
        if(!hasAdminRole(holder)) {
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", holder.getPermissionHolderName()), holder);
        }
    }
    
    /**
     * Does this permission holder have any role defined in this permission?
     * @param holder
     * @param createPermissionDefinition
     * @return
     */
    public boolean hasPermission(PermissionHolder holder, PermissionDefinition permission) {
        Set<RoleVO> roles = permission.getPermission().getRoles();
        return hasAnyRole(holder, roles);
    }
    
    /**
     * Return all the granted permissions a user has.  This is any Permission Definition that the user
     *  has permission for.
     *  
     *  TODO all VO types too?
     *  
     * @param holder
     * @return
     */
    public Set<MangoPermission> getGrantedPermissions(PermissionHolder holder){
        Set<MangoPermission> grantedPermissions = new HashSet<>();

        for(Entry<String, PermissionDefinition> def : ModuleRegistry.getPermissionDefinitions().entrySet()) {
            MangoPermission permission = def.getValue().getPermission();
            Set<RoleVO> roles = permission.getRoles();
            if(hasAnyRole(holder, roles)) {
                grantedPermissions.add(permission);
            }
        }
        return grantedPermissions;
    }
    
    /**
     * Does this permission holder have any role for the permission 
     *  type on this vo?
     *  
     * @param holder
     * @param vo
     * @param permissionType
     * @return
     */
    public boolean hasPermission(PermissionHolder holder, AbstractVO<?> vo, String permissionType) {
        Set<RoleVO> roles = roleDao.getRoles(vo, permissionType);
        return hasAnyRole(holder, roles);
    }
    
    /**
     * Ensure this user have the global data source permission
     * @param user
     * @throws PermissionException
     */
    public void ensureDataSourcePermission(PermissionHolder user) throws PermissionException {
        if (!hasDataSourcePermission(user))
            throw new PermissionException(new TranslatableMessage("permission.exception.editAnyDataSource", user.getPermissionHolderName()), user);
    }

    /**
     * Does this user have the global data source permission?
     * @param user
     * @return
     * @throws PermissionException
     */
    public boolean hasDataSourcePermission(PermissionHolder user) throws PermissionException {
        if (!isValidPermissionHolder(user)) return false;
        
        if(user.hasAdminRole()) return true;
        
        return hasPermission(user, dataSourcePermission);
    }
    
    /**
     * Ensure the user can edit this data source.
     *  This method is more performant if you only have the data source ID as it 
     *  won't need to lookup the entire data source to get the permission.
     * @param user
     * @param dataSourceId
     * @throws PermissionException
     */
    public void ensureDataSourcePermission(PermissionHolder user, int dataSourceId) throws PermissionException {
        if (!hasDataSourcePermission(user, dataSourceId)) {
            throw new PermissionException(new TranslatableMessage("permission.exception.editDataSource", user.getPermissionHolderName()), user);
        }
    }

    /**
     * Ensure the user can edit this data source
     * @param user
     * @param ds
     * @throws PermissionException
     */
    public void ensureDataSourcePermission(PermissionHolder user, DataSourceVO<?> ds) throws PermissionException {
        if (!hasDataSourcePermission(user, ds))
            throw new PermissionException(new TranslatableMessage("permission.exception.editDataSource", user.getPermissionHolderName()), user);
    }

    /**
     * Does this permission holder have any of the edit roles on the data source?
     * @param user
     * @param ds
     * @return
     * @throws PermissionException
     */
    public boolean hasDataSourcePermission(PermissionHolder user, DataSourceVO<?> ds) throws PermissionException {
        return hasAnyRole(user, ds.getEditRoles());
    }
    
    /**
     * Does this permission holder have any of the edit roles on the data source?
     *  This method is more performant if you only have the data source ID as it 
     *  won't need to lookup the entire data source to get the permission.
     * @param user
     * @param dsId
     * @return
     * @throws PermissionException
     */
    public boolean hasDataSourcePermission(PermissionHolder user, int dsId) throws PermissionException {
        Set<RoleVO> editRoles = roleDao.getRoles(dsId, DataSourceVO.class.getSimpleName(), EDIT);
        return hasAnyRole(user, editRoles);
    }
    
    //
    //
    // Data point access
    //
    /**
     * Ensure the PermissionHolder can read the data point
     * @param user
     * @param point
     * @throws PermissionException
     */
    public void ensureDataPointReadPermission(PermissionHolder user, IDataPoint point) throws PermissionException {
        if (!hasDataPointReadPermission(user, point)) {
            throw new PermissionException(new TranslatableMessage("permission.exception.readDataPoint", user.getPermissionHolderName()), user);
        }
    }

    /**
     * Can this PermissionHolder read the data point?
     * @param user
     * @param point
     * @return
     * @throws PermissionException
     */
    public boolean hasDataPointReadPermission(PermissionHolder user, IDataPoint point) throws PermissionException {
        if (hasAnyRole(user, point.getReadRoles())) {
            return true;
        }
        return hasDataPointSetPermission(user, point);
    }

    /**
     * Can this PermissionHolder read this data point?  
     *  This method is more performant if you only have the data point ID as it 
     *  won't need to lookup the entire point to get the permission.
     * @param user
     * @param dataPointId
     * @throws PermissionException
     */
    public void ensureDataPointReadPermission(PermissionHolder user, int dataPointId) throws PermissionException {
        if (!hasDataPointReadPermission(user, dataPointId)) {
            throw new PermissionException(new TranslatableMessage("permission.exception.readDataPoint", user.getPermissionHolderName()), user);
        }
    }
    
    /**
     * Can this PermissionHolder read this data point?  
     *  This method is more performant if you only have the data point ID as it 
     *  won't need to lookup the entire point to get the permission.
     * @param user
     * @param dataPointId
     * @return
     * @throws PermissionException
     */
    public boolean hasDataPointReadPermission(PermissionHolder user, int dataPointId) throws PermissionException {
        Set<RoleVO> editRoles = roleDao.getRoles(dataPointId, DataPointVO.class.getSimpleName(), READ);
        return hasAnyRole(user, editRoles);
    }
    
    /**
     * Ensure this PermissionHolder set values on this data point.
     * @param user
     * @param point
     * @throws PermissionException
     */
    public void ensureDataPointSetPermission(PermissionHolder user, DataPointVO point) throws PermissionException {
        if (!hasDataPointSetPermission(user, point))
            throw new PermissionException(new TranslatableMessage("permission.exception.setDataPoint", user.getPermissionHolderName()), user);
    }

    /**
     * Can this PermissionHolder set values on this data point?
     * @param user
     * @param point
     * @return
     * @throws PermissionException
     */
    public boolean hasDataPointSetPermission(PermissionHolder user, IDataPoint point) throws PermissionException {
        if (hasAnyRole(user, point.getSetRoles())) {
            return true;
        }
        return hasDataSourcePermission(user, point.getDataSourceId());
    }

    /**
     * Can this PermissionHolder read this data point?  
     *  This method is more performant if you only have the data point ID as it 
     *  won't need to lookup the entire point to get the permission.
     * @param user
     * @param dataPointId
     * @throws PermissionException
     */
    public void ensureDataPointSetPermission(PermissionHolder user, int dataPointId) throws PermissionException {
        if (!hasDataPointSetPermission(user, dataPointId)) {
            throw new PermissionException(new TranslatableMessage("permission.exception.setDataPoint", user.getPermissionHolderName()), user);
        }
    }
    
    /**
     * Can this PermissionHolder read this data point?  
     *  This method is more performant if you only have the data point ID as it 
     *  won't need to lookup the entire point to get the permission.
     * @param user
     * @param dataPointId
     * @return
     * @throws PermissionException
     */
    public boolean hasDataPointSetPermission(PermissionHolder user, int dataPointId) throws PermissionException {
        Set<RoleVO> editRoles = roleDao.getRoles(dataPointId, DataPointVO.class.getSimpleName(), SET);
        return hasAnyRole(user, editRoles);
    }
    
    /**
     * Does this holder have access to view this event type?
     * @param user
     * @param eventType
     * @return
     */
    public boolean hasEventTypePermission(PermissionHolder user, EventType eventType) {
        return hasAdminRole(user) || eventType.hasPermission(user);
    }

    /**
     * Ensure this holder has access to view this event type
     * @param user
     * @param eventType
     * @throws PermissionException
     */
    public void ensureEventTypePermission(PermissionHolder user, EventType eventType) throws PermissionException {
        if (!hasEventTypePermission(user, eventType))
            throw new PermissionException(new TranslatableMessage("permission.exception.event", user.getPermissionHolderName()), user);
    }

    /**
     * Ensure this holder has access to view this event type VO
     * @param user
     * @param eventType
     * @throws PermissionException
     */
    public void ensureEventTypePermission(PermissionHolder user, EventTypeVO eventType) throws PermissionException {
        ensureEventTypePermission(user, eventType.getEventType());
    }
    
    /**
     * Does this permission holder have at least one of the required roles
     * @param user
     * @param requiredRoles
     * @return
     */
    public boolean hasAnyRole(PermissionHolder user, Set<RoleVO> requiredRoles) {
        if (!isValidPermissionHolder(user)) return false;

        Set<RoleVO> heldRoles = user.getRoles();
        return containsAnyRole(heldRoles, requiredRoles);
    }
    
    /**
     * Ensure this user has at least one of the roles
     * @param user
     * @param requiredPermissions
     */
    public void ensureHasAnyRole(PermissionHolder user, Set<RoleVO> requiredRoles) {
        if (!hasAnyRole(user, requiredRoles)) {
            ensureValidPermissionHolder(user);
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", user.getPermissionHolderName()), user);
        }
    }
    
    /**
     * Does this permission holder have this exact role
     * @param user
     * @param requiredRole
     * @return
     */
    public boolean hasSingleRole(PermissionHolder user, RoleVO requiredRole) {
        if (!isValidPermissionHolder(user)) return false;

        Set<RoleVO> heldRoles = user.getRoles();
        return containsSingleRole(heldRoles, requiredRole);
    }
    
    /**
     * Ensure this holder has the required role role
     * @param holder
     */
    public void ensureSingleRole(PermissionHolder holder, RoleVO requiredRole) throws PermissionException {
        if(!hasSingleRole(holder, requiredRole)) {
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", holder.getPermissionHolderName()), holder);
        }
    }
    
    /**
     * Does this permission holder have all the required roles?
     * @param user
     * @param requiredRoles
     * @return
     */
    public boolean hasAllRoles(PermissionHolder user, Set<RoleVO> requiredRoles) {
        if (!isValidPermissionHolder(user)) return false;

        Set<RoleVO> heldRoles = user.getRoles();
        return containsAll(heldRoles, requiredRoles);
    }
    
    /**
     * Ensure this holder has all the required roles
     * @param user
     * @param requiredRoles
     */
    public void ensureHasAllRoles(PermissionHolder user, Set<RoleVO> requiredRoles) {
        if (!hasAllRoles(user, requiredRoles)) {
            ensureValidPermissionHolder(user);
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", user.getPermissionHolderName()), user);
        }
    }
    
    /**
     * Ensure this permission holder is valid
     * @param user
     */
    public void ensureValidPermissionHolder(PermissionHolder user)  throws PermissionException {
        if (user == null)
            throw new PermissionException(new TranslatableMessage("permission.exception.notAuthenticated"), null);
        if (user.isPermissionHolderDisabled())
            throw new PermissionException(new TranslatableMessage("permission.exception.userIsDisabled"), user);
    }
    
    /**
     * Is this permission holder valid, to be valid they:
     * - must be non null
     * - must not disabled
     * 
     * @param user
     * @return
     */
    public boolean isValidPermissionHolder(PermissionHolder user) {
        return !(user == null || user.isPermissionHolderDisabled());
    }
    
    /**
     * Is this required role in the held roles? 
     * @param heldRoles
     * @param requiredRole
     * @return
     */
    private boolean containsSingleRole(Set<RoleVO> heldRoles, RoleVO requiredRole) {
        if (heldRoles.contains(roleDao.getSuperadminRole())) {
            return true;
        }

        // empty permissions string indicates that only superadmins are allowed access
        if (requiredRole == null) {
            return false;
        }

        return heldRoles.contains(requiredRole);
    }

    /**
     * Is every required role in the held roles?
     * @param heldRoles
     * @param requiredRoles
     * @return
     */
    private boolean containsAll(Set<RoleVO> heldRoles, Set<RoleVO> requiredRoles) {
        checkRoleSet(requiredRoles);
        
        if (heldRoles.contains(roleDao.getSuperadminRole())) {
            return true;
        }

        // empty permissions string indicates that only superadmins are allowed access
        if (requiredRoles.isEmpty()) {
            return false;
        }
        
        for(RoleVO role : requiredRoles) {
            if(!heldRoles.contains(role)) {
               return false; 
            }
        }
        return true;
    }
    
    /**
     * Is any required role in the held roles?
     * @param heldRoles
     * @param requiredRoles
     * @return
     */
    private boolean containsAnyRole(Set<RoleVO> heldRoles, Set<RoleVO> requiredRoles) {
        checkRoleSet(requiredRoles);
        //If I am superadmin or this has the default user role the we are good
        if (heldRoles.contains(roleDao.getSuperadminRole()) || requiredRoles.contains(roleDao.getUserRole())) {
            return true;
        }

        // empty roles indicates that only superadmins are allowed access
        if (requiredRoles.isEmpty()) {
            return false;
        }

        for (RoleVO requiredRole : requiredRoles) {
            if (heldRoles.contains(requiredRole)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate roles.  This will validate that:
     *
     *   1. the new permissions are non null
     *   2. all new permissions are not empty
     *   3. the new permissions do not contain spaces
     *   (then for non admin/owners)
     *   4. the saving user will at least retain one permission
     *   5. the user cannot not remove an existing permission they do not have
     *   6. the user has all of the new permissions being added
     *
     *   If the saving user is also the owner, then the new permissions need not contain
     *   one of the user's roles
     *
     * @param result - the result of the validation
     * @param contextKey - the key to apply the messages to
     * @param holder - the saving permission holder
     * @param savedByOwner - is the saving user the owner of this item (use false if no owner is possible)
     * @param existingRoles - the currently saved permissions
     * @param newRoles - the new permissions to validate
     */
    public void validateVoRoles(ProcessResult result, String contextKey, PermissionHolder holder,
            boolean savedByOwner, Set<RoleVO> existingRoles, Set<RoleVO> newRoles) {
        if (holder == null) {
            result.addContextualMessage(contextKey, "validate.userRequired");
            return;
        }

        if(newRoles == null) {
            result.addContextualMessage(contextKey, "validate.invalidValue");
            return;
        }

        for (RoleVO role : newRoles) {
            if (role == null) {
                result.addContextualMessage(contextKey, "validate.role.empty");
                return;
            } else if(roleDao.getIdByXid(role.getXid()) == null) {
                result.addContextualMessage(contextKey, "validate.role.notFound", role.getXid());
            }
        }
        
        if(holder.hasAdminRole())
            return;

        //Ensure the holder has at least one of the new permissions
        if(!savedByOwner && !newRoles.contains(roleDao.getUserRole()) && Collections.disjoint(holder.getRoles(), newRoles)) {
            result.addContextualMessage(contextKey, "validate.mustRetainPermission");
        }

        if(existingRoles != null) {
            //Check for permissions being added that the user does not have
            Set<RoleVO> added = new HashSet<>(newRoles);
            added.removeAll(existingRoles);
            added.removeAll(holder.getRoles());
            if(added.size() > 0) {
                result.addContextualMessage(contextKey, "validate.role.invalidModification", implodeRoles(holder.getRoles()));
            }
            //Check for permissions being removed that the user does not have
            Set<RoleVO> removed = new HashSet<>(existingRoles);
            removed.removeAll(newRoles);
            removed.removeAll(holder.getRoles());
            if(removed.size() > 0) {
                result.addContextualMessage(contextKey, "validate.role.invalidModification", implodeRoles(holder.getRoles()));
            }
        }
        return;
    }
    
    /**
     * Check a role set so that
     *  - set cannot be null
     *  - role in set cannot be null
     *  - xid of role cannot be null
     * @param requiredPermissions
     */
    private static void checkRoleSet(Set<RoleVO> requiredRoles) {
        Objects.requireNonNull(requiredRoles, "Role set cannot be null");

        for (RoleVO requiredRole : requiredRoles) {
            if (requiredRole == null || requiredRole.getXid().isEmpty()) {
                throw new IllegalArgumentException("Role in set cannot be null or have empty role");
            }
        }
    }
    
    /**
     * Turn a set of roles into a comma separated list for display in a message
     * @param roles
     * @return
     */
    public String implodeRoles(Set<RoleVO> roles) {
        checkRoleSet(roles);
        return String.join(",", roles.stream().map(role -> role.getXid()).collect(Collectors.toSet()));
    }
    
    /**
     * Explode a comma separated group of permissions (roles) from the legacy format
     * @param groups
     * @return
     */
    public Set<String> explodeLegacyPermissionGroups(String groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> set = new HashSet<>();
        for (String s : groups.split(",")) {
            s = s.trim();
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return Collections.unmodifiableSet(set);
    }
}
