/*
 *  Copyright (C) 2013 Deltamation Software. All rights reserved.
 *  @author Jared Wiltshire
 */
package com.serotonin.m2m2.db.dao;

import java.util.ArrayList;
import java.util.List;

import org.jooq.Record;
import org.jooq.Select;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteautomation.mango.spring.db.AbstractTableDefinition;
import com.infiniteautomation.mango.spring.events.DaoEvent;
import com.infiniteautomation.mango.spring.events.DaoEventType;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.rt.event.type.AuditEventType;
import com.serotonin.m2m2.vo.AbstractVO;

/**
 * Provides an API to retrieve, update and save
 * VO objects from and to the database.
 *
 * Copyright (C) 2013 Deltamation Software. All Rights Reserved.
 *
 * @author Jared Wiltshire
 */
public abstract class AbstractDao<T extends AbstractVO<?>> extends AbstractBasicDao<T> implements AbstractVOAccess<T> {

    /**
     * For generating XIDs this is prepended to any XIDs generated
     */
    protected final String xidPrefix;
    /**
     * Audit event type name
     */
    protected final String typeName; //Type name for Audit Events

    /**
     * 
     */
    protected final AbstractTableDefinition table;
    
    protected AbstractDao(String typeName, AbstractTableDefinition table, ObjectMapper mapper, ApplicationEventPublisher publisher) {
        this(typeName, table, null, mapper, publisher);
    }
    
    /**
     * 
     * @param typeName
     * @param table
     * @param tableAlias
     * @param extraProperties
     * @param countMonitorName - If not null create a monitor to track table row count
     * @param mapper
     * @param publisher
     */
    protected AbstractDao(String typeName, AbstractTableDefinition table,
            TranslatableMessage countMonitorName,
            ObjectMapper mapper, ApplicationEventPublisher publisher) {
        super(table, countMonitorName, mapper, publisher);
        this.table = table;
        this.xidPrefix = getXidPrefix();
        this.typeName = typeName;
    }



    /**
     * Gets the XID prefix for XID generation
     *
     * @return XID prefix, null if XIDs not supported
     */
    protected abstract String getXidPrefix();
    
    @Override
    public String generateUniqueXid() {
        if (xidPrefix == null) {
            return null;
        }
        return generateUniqueXid(xidPrefix, this.table.getTable().getName());
    }

    @Override
    public boolean isXidUnique(String xid, int excludeId) {
        return isXidUnique(xid, excludeId, this.table.getTable().getName());
    }

    @Override
    public T getByXid(String xid) {
        if (xid == null || this.table.getField("xid") == null) {
            return null;
        }
        Select<Record> query = this.getSelectQuery()
                .where(table.getXidAlias().eq(xid))
                .limit(1);
        String sql = query.getSQL();
        List<Object> args = query.getBindValues();
        T vo = ejt.query(sql, args.toArray(new Object[args.size()]), getObjectResultSetExtractor());
        if(vo != null) {
            loadRelationalData(vo);
        }
        return vo;
    }

    @Override
    public List<T> getByName(String name) {
        if (name == null || this.table.getField("name") == null) {
            return null;
        }
        Select<Record> query = this.getSelectQuery()
                .where(this.table.getField("name").eq(name));
        String sql = query.getSQL();
        List<Object> args = query.getBindValues();
        List<T> items = new ArrayList<>();
        query(sql, args.toArray(new Object[args.size()]), getCallbackResultSetExtractor((item, index)->{
            loadRelationalData(item);
            items.add(item);
        }));
        return items;
    }

    @Override
    public Integer getIdByXid(String xid) {
        return this.create.select(this.table.getIdAlias()).from(this.table.getTableAsAlias())
                .where(this.table.getXidAlias().eq(xid))
                .limit(1).fetchOneInto(Integer.class);
    }

    @Override
    public String getXidById(int id) {
        return this.create.select(this.table.getIdAlias()).from(this.table.getTableAsAlias())
                .where(this.table.getIdAlias().eq(id))
                .limit(1).fetchOneInto(String.class);
    }

    @Override
    public boolean delete(T vo) {
        if(super.delete(vo)) {
            AuditEventType.raiseDeletedEvent(this.typeName, vo);
            return true;
        }else {
            return false;
        }
    }
    
    @Override
    public void insert(T vo) {
        if (vo.getXid() == null) {
            vo.setXid(generateUniqueXid());
        }
        super.insert(vo);
        AuditEventType.raiseAddedEvent(this.typeName, vo);
    }
    
    @Override
    public void update(T existing, T vo) {
        if (vo.getXid() == null) {
            vo.setXid(existing.getXid());
        }
        super.update(existing, vo);
        AuditEventType.raiseChangedEvent(this.typeName, existing, vo);
    }
    
    /**
     * Creates a new vo by copying an existing one
     *
     * @param existingId
     *            ID of existing vo
     * @param newXid
     *            XID for the new vo
     * @param newName
     *            Name for the new vo
     * @return Copied vo with new XID and name
     * @param full - copy FKs?
     * @return
     */
    public int copy(final int existingId, final String newXid, final String newName, boolean full) {
        TransactionCallback<Integer> callback = new TransactionCallback<Integer>() {
            @Override
            public Integer doInTransaction(TransactionStatus status) {
                T vo = get(existingId);

                // Copy the vo
                @SuppressWarnings("unchecked")
                T copy = (T)vo.copy();
                copy.setId(Common.NEW_ID);
                copy.setXid(newXid);
                copy.setName(newName);
                insert(copy);
                
                // Copy permissions.
                return copy.getId();
            }
        };

        return getTransactionTemplate().execute(callback);
    }
    
    @Override
    protected DaoEvent<T> createDaoEvent(DaoEventType type, T vo, T existing) {
        switch(type) {
            case CREATE:
                return new DaoEvent<T>(this, type, vo, null);
            case UPDATE:
                return new DaoEvent<T>(this, type, vo, existing.getXid());
            case DELETE:
                return new DaoEvent<T>(this, type, vo, existing.getXid());
            default:
                throw new ShouldNeverHappenException("Uknown dao event type: " + type);
        }
        
    }

}
