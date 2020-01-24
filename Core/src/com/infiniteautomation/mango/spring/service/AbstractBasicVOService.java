/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.infiniteautomation.mango.db.query.ConditionSortLimit;
import com.infiniteautomation.mango.util.exception.NotFoundException;
import com.infiniteautomation.mango.util.exception.ValidationException;
import com.serotonin.db.MappedRowCallback;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.AbstractBasicVOAccess;
import com.serotonin.m2m2.db.dao.RoleDao.RoleDeletedDaoEvent;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.vo.AbstractBasicVO;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.role.Role;
import com.serotonin.m2m2.vo.role.RoleVO;

import net.jazdw.rql.parser.ASTNode;

/**
 * @author Terry Packer
 *
 */
public abstract class AbstractBasicVOService<T extends AbstractBasicVO, DAO extends AbstractBasicVOAccess<T>> {

    protected final DAO dao;
    protected final PermissionService permissionService;

    /**
     * Service
     * @param dao
     * @param permissionService
     * @param createPermissionDefinition
     */
    public AbstractBasicVOService(DAO dao, PermissionService permissionService) {
        this.dao = dao;
        this.permissionService = permissionService;
    }

    /**
     * Validate a new VO
     * @param vo
     * @param user
     * @return
     */
    abstract public ProcessResult validate(T vo, PermissionHolder user);

    /**
     * Can this user edit this VO
     * 
     * @param user
     * @param vo
     * @return
     */
    abstract public  boolean hasEditPermission(PermissionHolder user, T vo);
    
    /**
     * Can this user read this VO
     * 
     * @param user
     * @param vo
     * @return
     */
    abstract public boolean hasReadPermission(PermissionHolder user, T vo);


    /**
     * Get any create permission roles
     *  override as necessary
     * @return
     */
    public Set<Role> getCreatePermissionRoles() {
        return Collections.emptySet();
    }
    
    /**
     * Handle when a role was deleted with the existing mappings at the time of deletion
     * You must annotate the overridden method with @EventListener in order for this to work.
     * @param event
     */
    protected void handleRoleDeletedEvent(RoleDeletedDaoEvent event) {
        
    }
    
    /**
     * A new role was created.
     *  Override as required
     * @param role
     */
    protected void roleCreated(RoleVO role) {
        
    }
    
    /**
     * A role was deleted, update any runtime members that have this role.
     *   The database will delete on cascade from the mapping table so this 
     *   only concerns runtime data.
     * @param role
     */
    protected void roleDeleted(RoleVO role) {
        
    }
    
    /**
     * A role was updated, only the name can be updated.
     *  Override as required
     * @param role
     */
    protected void roleUpdated(RoleVO role) {
        
    }
    
    /**
     * Ensure this vo is valid compared to the previous one. 
     * 
     * Override as necessary, most VOs won't need this.
     * 
     * @param existing
     * @param vo
     * @param user
     * @return
     */
    public ProcessResult validate(T existing, T vo, PermissionHolder user) {
        return validate(vo, user);
    }
    
    /**
     * Ensure that this VO is valid.
     * Note: validation will only fail if there are Error level messages in the result
     * @param vo
     * @param user
     */
    public void ensureValid(T vo, PermissionHolder user) throws ValidationException {
        ProcessResult result = validate(vo, user);
        if(!result.isValid())
            throw new ValidationException(result);
    }  
    
    /**
     * Note: validation will only fail if there are Error level messages in the result
     * @param existing
     * @param vo
     * @param user
     * @throws ValidationException
     */
    public void ensureValid(T existing, T vo, PermissionHolder user) throws ValidationException {
        ProcessResult result = validate(existing, vo, user);
        if(!result.isValid())
            throw new ValidationException(result);
    }
    
    /**
     * 
     * @param id
     * @return
     * @throws NotFoundException
     * @throws PermissionException
     */
    public T get(int id) throws NotFoundException, PermissionException {
        T vo = dao.get(id);
        if(vo == null)
            throw new NotFoundException();
        
        PermissionHolder user = Common.getUser();
        Objects.requireNonNull(user, "Permission holder must be set in security context");
        
        ensureReadPermission(user, vo);
        return vo;
    }
    
    /**
     * 
     * @param vo
     * @return
     * @throws PermissionException
     * @throws ValidationException
     */
    public T insert(T vo) throws PermissionException, ValidationException {
        PermissionHolder user = Common.getUser();
        Objects.requireNonNull(user, "Permission holder must be set in security context");

        //Ensure they can create
        ensureCreatePermission(user, vo);
        
        //Ensure id is not set
        if(vo.getId() != Common.NEW_ID) {
            ProcessResult result = new ProcessResult();
            result.addContextualMessage("id", "validate.invalidValue");
            throw new ValidationException(result);
        }
        
        ensureValid(vo, user);
        dao.insert(vo);
        return vo;
    }
    
    /**
     * 
     * @param existingId
     * @param vo
     * @return
     * @throws PermissionException
     * @throws ValidationException
     */
    public T update(int existingId, T vo) throws PermissionException, ValidationException {
        return update(get(existingId), vo);
    }
    
    /**
     * 
     * @param existing
     * @param vo
     * 
     * @return
     * @throws PermissionException
     * @throws ValidationException
     */
    public T update(T existing, T vo) throws PermissionException, ValidationException {
        PermissionHolder user = Common.getUser();
        Objects.requireNonNull(user, "Permission holder must be set in security context");
        
        ensureEditPermission(user, existing);
        
        vo.setId(existing.getId());
        ensureValid(existing, vo, user);
        dao.update(existing, vo);
        return vo;
    }
    
    /**
     * 
     * @param id
     * @return
     * @throws PermissionException
     * @throws NotFoundException
     */
    public T delete(int id) throws PermissionException, NotFoundException {
        T vo = get(id);
        return delete(vo);
    }
    
    /**
     * 
     * @param vo
     * @return
     * @throws PermissionException
     * @throws NotFoundException
     */
    public T delete(T vo) throws PermissionException, NotFoundException {
        PermissionHolder user = Common.getUser();
        Objects.requireNonNull(user, "Permission holder must be set in security context");
        
        ensureDeletePermission(user, vo);
        dao.delete(vo.getId());
        return vo;
    }
    
    /**
     * Query for VOs and optionally load the relational info
     * @param conditions
     * @param callback
     */
    public void customizedQuery(ConditionSortLimit conditions, MappedRowCallback<T> callback) {
        dao.customizedQuery(conditions, (item, index) ->{
            dao.loadRelationalData(item);
            callback.row(item, index);
        });
    }
    
    /**
     * Query for VOs and optionally load the relational info
     * @param conditions
     * @param full - load relational data
     * @param callback
     */
    public void customizedQuery(ASTNode conditions, MappedRowCallback<T> callback) {
        dao.customizedQuery(dao.rqlToCondition(conditions), (item, index) ->{
            dao.loadRelationalData(item);
            callback.row(item, index);
        });
    }
    
    /**
     * Count VOs
     * @param conditions
     * @return
     */
    public int customizedCount(ConditionSortLimit conditions) {
        return dao.customizedCount(conditions);
    }
    
    /**
     * Count VOs
     * @param conditions - RQL AST Node
     * @return
     */
    public int customizedCount(ASTNode conditions) {
        return dao.customizedCount(dao.rqlToCondition(conditions));
    }
    
    /**
     * Can this user create this VO
     * 
     * @param user
     * @param vo to insert
     * @return
     */
    public boolean hasCreatePermission(PermissionHolder user, T vo) {
        return permissionService.hasAnyRole(user, getCreatePermissionRoles());
    }

    /**
     * Optionally override to have specific delete permissions, default is same as edit
     * @param user
     * @param vo
     * @return
     */
    public boolean hasDeletePermission(PermissionHolder user, T vo) {
        return hasEditPermission(user, vo);
    }
    
    /**
     * Ensure this user can create this vo
     * 
     * @param user
     * @throws PermissionException
     */
    public void ensureCreatePermission(PermissionHolder user, T vo) throws PermissionException {
        if(!hasCreatePermission(user, vo))
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", user.getPermissionHolderName()), user);
    }
    
    /**
     * Ensure this user can edit this vo
     * 
     * @param user
     * @param vo
     */
    public void ensureEditPermission(PermissionHolder user, T vo) throws PermissionException {
        if(!hasEditPermission(user, vo))
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", user.getPermissionHolderName()), user);
    }
    
    /**
     * Ensure this user can read this vo
     * 
     * @param user
     * @param vo
     * @throws PermissionException
     */
    public void ensureReadPermission(PermissionHolder user, T vo) throws PermissionException {
        if(!hasReadPermission(user, vo))
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", user.getPermissionHolderName()), user);
    }
    
    /**
     * Ensure this user can delete this vo
     * @param user
     * @param vo
     * @throws PermissionException
     */
    public void ensureDeletePermission(PermissionHolder user, T vo) throws PermissionException {
        if(!hasDeletePermission(user, vo))
            throw new PermissionException(new TranslatableMessage("permission.exception.doesNotHaveRequiredPermission", user.getPermissionHolderName()), user);
    }
    
    /**
     * Get the DAO
     * @return
     */
    public DAO getDao() {
        return dao;
    }
    
}
