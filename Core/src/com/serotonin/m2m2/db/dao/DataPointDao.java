/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.db.dao;

import static com.serotonin.m2m2.db.dao.DataPointTagsDao.DATA_POINT_TAGS_PIVOT_ALIAS;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectConnectByStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectLimitStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infiniteautomation.mango.db.query.ConditionSortLimit;
import com.infiniteautomation.mango.db.query.ConditionSortLimitWithTagKeys;
import com.infiniteautomation.mango.db.query.Index;
import com.infiniteautomation.mango.db.query.QueryAttribute;
import com.infiniteautomation.mango.db.query.RQLToCondition;
import com.infiniteautomation.mango.db.query.RQLToConditionWithTagKeys;
import com.infiniteautomation.mango.spring.MangoRuntimeContextConfiguration;
import com.infiniteautomation.mango.spring.db.DataPointTableDefinition;
import com.infiniteautomation.mango.spring.db.DataSourceTableDefinition;
import com.infiniteautomation.mango.spring.db.EventDetectorTableDefinition;
import com.infiniteautomation.mango.spring.events.DaoEvent;
import com.infiniteautomation.mango.spring.events.DaoEventType;
import com.infiniteautomation.mango.spring.events.DataPointTagsUpdatedEvent;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.LazyInitSupplier;
import com.infiniteautomation.mango.util.usage.DataPointUsageStatistics;
import com.serotonin.ModuleNotLoadedException;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.db.MappedRowCallback;
import com.serotonin.log.LogStopWatch;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.DataTypes;
import com.serotonin.m2m2.IMangoLifecycle;
import com.serotonin.m2m2.LicenseViolatedException;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.module.DataPointChangeDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.event.type.AuditEventType;
import com.serotonin.m2m2.rt.event.type.EventType;
import com.serotonin.m2m2.vo.DataPointSummary;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.bean.PointHistoryCount;
import com.serotonin.m2m2.vo.comment.UserCommentVO;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.event.detector.AbstractEventDetectorVO;
import com.serotonin.m2m2.vo.event.detector.AbstractPointEventDetectorVO;
import com.serotonin.provider.Providers;
import com.serotonin.util.SerializationHelper;

import net.jazdw.rql.parser.ASTNode;

/**
 * This class is a Half-Breed between the legacy Dao and the new type that extends AbstractDao.
 *
 * The top half of the code is the legacy code, the bottom is the new style.
 *
 * Eventually all the method innards will be reworked, leaving the names the same.
 *
 * @author Terry Packer
 *
 */
@Repository()
public class DataPointDao extends AbstractDao<DataPointVO, DataPointTableDefinition> {
    static final Log LOG = LogFactory.getLog(DataPointDao.class);

    private final DataSourceTableDefinition dataSourceTable;
    private final EventDetectorTableDefinition eventDetectorTable;

    private static final LazyInitSupplier<DataPointDao> springInstance = new LazyInitSupplier<>(() -> {
        Object o = Common.getRuntimeContext().getBean(DataPointDao.class);
        if(o == null)
            throw new ShouldNeverHappenException("DAO not initialized in Spring Runtime Context");
        return (DataPointDao)o;
    });

    //TODO Clean up/remove
    public static final Name DATA_POINTS_ALIAS = DSL.name("dp");
    public static final Table<Record> DATA_POINTS = DSL.table(DSL.name(SchemaDefinition.DATAPOINTS_TABLE)).as(DATA_POINTS_ALIAS);
    public static final Field<Integer> ID = DSL.field(DATA_POINTS_ALIAS.append("id"), SQLDataType.INTEGER.nullable(false));
    public static final Field<Integer> DATA_SOURCE_ID = DSL.field(DATA_POINTS_ALIAS.append("dataSourceId"), SQLDataType.INTEGER.nullable(false));
    public static final Field<String> READ_PERMISSION = DSL.field(DATA_POINTS_ALIAS.append("readPermission"), SQLDataType.VARCHAR(255).nullable(true));
    public static final Field<String> SET_PERMISSION = DSL.field(DATA_POINTS_ALIAS.append("setPermission"), SQLDataType.VARCHAR(255).nullable(true));

    @Autowired
    private DataPointDao(DataPointTableDefinition table,
            DataSourceTableDefinition dataSourceTable,
            EventDetectorTableDefinition eventDetectorTable,
            @Qualifier(MangoRuntimeContextConfiguration.DAO_OBJECT_MAPPER_NAME)ObjectMapper mapper,
            ApplicationEventPublisher publisher) {
        super(EventType.EventTypeNames.DATA_POINT, table,
                new TranslatableMessage("internal.monitor.DATA_POINT_COUNT"),
                mapper, publisher);
        this.dataSourceTable = dataSourceTable;
        this.eventDetectorTable = eventDetectorTable;
    }

    /**
     * Get cached instance from Spring Context
     * @return
     */
    public static DataPointDao getInstance() {
        return springInstance.get();
    }

    //
    //
    // Data Points
    //
    /**
     * Get data points for a data source
     * @param dataSourceId
     * @param includeRelationalData
     * @return
     */
    public List<DataPointVO> getDataPoints(int dataSourceId) {
        return this.customizedQuery(getJoinedSelectQuery().where(this.table.getAlias("dataSourceId").eq(dataSourceId)),
                getListResultSetExtractor());
    }

    /**
     * Get all data point Ids in the table
     * @return
     */
    public List<Integer> getDataPointIds(){
        return queryForList("SELECT id FROM dataPoints" , Integer.class);
    }

    /**
     * TODO Mango 4.0 ALWAYS use this JOIN, make the default query since
     *  we are loading full VOs always now.  Likely need to review and optimize anyway.
     * Get points for runtime in an efficient manner
     * @param dataSourceId
     * @return
     */
    public List<DataPointVO> getDataPointsForDataSourceStart(int dataSourceId) {
        List<Field<?>> fields = new ArrayList<>(this.getSelectFields());
        fields.addAll(this.eventDetectorTable.getSelectFields());

        Select<Record> select = this.joinTables(this.getSelectQuery(fields), null).leftOuterJoin(this.eventDetectorTable.getTableAsAlias())
                .on(this.table.getIdAlias().eq(this.eventDetectorTable.getField("dataPointId")))
                .where(this.table.getAlias("dataSourceId").eq(dataSourceId));

        return this.customizedQuery(select, new DataPointStartupResultSetExtractor());
    }

    class DataPointStartupResultSetExtractor implements ResultSetExtractor<List<DataPointVO>> {

        private final int firstEventDetectorColumn;
        private final EventDetectorRowMapper<?> eventRowMapper;

        public DataPointStartupResultSetExtractor() {
            this.firstEventDetectorColumn = getSelectFields().size() + 1;
            this.eventRowMapper = new EventDetectorRowMapper<>(this.firstEventDetectorColumn, 5);
        }

        @Override
        public List<DataPointVO> extractData(ResultSet rs) throws SQLException, DataAccessException {
            Map<Integer, DataPointVO> result = new HashMap<Integer, DataPointVO>();
            DataPointMapper pointRowMapper = new DataPointMapper();
            while(rs.next()) {
                int id = rs.getInt(1); //dp.id column number
                if(result.containsKey(id))
                    try{
                        addEventDetector(result.get(id), rs);
                    }catch(Exception e){
                        LOG.error("Point not fully initialized: " + e.getMessage(), e);
                    }
                else {
                    DataPointVO dpvo = pointRowMapper.mapRow(rs, rs.getRow());
                    dpvo.setEventDetectors(new ArrayList<AbstractPointEventDetectorVO<?>>());
                    loadRelationalData(dpvo);
                    result.put(id, dpvo);
                    try{
                        addEventDetector(dpvo, rs);
                    }catch(Exception e){
                        LOG.error("Point not fully initialized: " + e.getMessage(), e);
                    }
                }
            }
            return new ArrayList<DataPointVO>(result.values());
        }

        private void addEventDetector(DataPointVO dpvo, ResultSet rs) throws SQLException {
            if(rs.getObject(firstEventDetectorColumn) == null)
                return;
            AbstractEventDetectorVO<?> edvo = eventRowMapper.mapRow(rs, rs.getRow());
            AbstractPointEventDetectorVO<?> ped = (AbstractPointEventDetectorVO<?>) edvo;
            dpvo.getEventDetectors().add(ped);
        }
    }

    /**
     * Check licensing before adding a point
     */
    private void checkAddPoint() {
        IMangoLifecycle lifecycle = Providers.get(IMangoLifecycle.class);
        Integer limit = lifecycle.dataPointLimit();
        if(limit != null && this.countMonitor.getValue() >= limit) {
            String licenseType;
            if(Common.license() != null)
                licenseType = Common.license().getLicenseType();
            else
                licenseType = "Free";
            throw new LicenseViolatedException(new TranslatableMessage("license.dataPointLimit", licenseType, limit));
        }
    }

    @Override
    public void insert(DataPointVO vo) {
        checkAddPoint();
        for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
            def.beforeInsert(vo);

        // Create a default text renderer
        if (vo.getTextRenderer() == null)
            vo.defaultTextRenderer();

        super.insert(vo);

        for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
            def.afterInsert(vo);
    }

    @Override
    public void update(DataPointVO existing, DataPointVO vo) {
        for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
            def.beforeUpdate(vo);

        //If have a new data type we will wipe our history
        if (existing.getPointLocator().getDataTypeId() != vo.getPointLocator().getDataTypeId())
            Common.databaseProxy.newPointValueDao().deletePointValues(vo.getId());

        super.update(existing, vo);

        for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
            def.afterUpdate(vo);
    }

    /**
     * Update the enabled column, should only be done via the runtime manager
     * @param dp
     */
    public void saveEnabledColumn(DataPointVO dp) {
        ejt.update("UPDATE dataPoints SET enabled=? WHERE id=?", new Object[]{boolToChar(dp.isEnabled()), dp.getId()});
        this.publishEvent(new DaoEvent<DataPointVO>(this, DaoEventType.UPDATE, dp, null));
        AuditEventType.raiseToggleEvent(AuditEventType.TYPE_DATA_POINT, dp);
    }

    /**
     * Is a data point enabled, returns false if point is disabled or DNE.
     * @param id
     * @return
     */
    public boolean isEnabled(int id) {
        return query("select dp.enabled from dataPoints as dp WHERE id=?", new Object[] {id}, new ResultSetExtractor<Boolean>() {

            @Override
            public Boolean extractData(ResultSet rs) throws SQLException, DataAccessException {
                if(rs.next()) {
                    return charToBool(rs.getString(1));
                }else
                    return false;
            }

        });
    }

    public void deleteDataPoints(final int dataSourceId) {
        List<DataPointVO> old = getDataPoints(dataSourceId);

        for (DataPointVO dp : old) {
            for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
                def.beforeDelete(dp.getId());
        }

        getTransactionTemplate().execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                List<Integer> pointIds = queryForList("select id from dataPoints where dataSourceId=?",
                        new Object[] { dataSourceId }, Integer.class);
                if (pointIds.size() > 0) {
                    String dataPointIdList = createDelimitedList(new HashSet<>(pointIds), ",", null);
                    dataPointIdList = "(" + dataPointIdList + ")";
                    ejt.update("delete from eventHandlersMapping where eventTypeName=? and eventTypeRef1 in " + dataPointIdList,
                            new Object[] { EventType.EventTypeNames.DATA_POINT });
                    ejt.update("delete from userComments where commentType=2 and typeKey in " + dataPointIdList);
                    ejt.update("delete from eventDetectors where dataPointId in " + dataPointIdList);
                    ejt.update("delete from dataPoints where id in " + dataPointIdList);
                }
            }
        });

        for (DataPointVO dp : old) {
            for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
                def.afterDelete(dp.getId());
            this.publishEvent(new DaoEvent<DataPointVO>(this, DaoEventType.DELETE, dp, null));
            AuditEventType.raiseDeletedEvent(AuditEventType.TYPE_DATA_POINT, dp);
            this.countMonitor.decrement();
        }
    }

    @Override
    public boolean delete(DataPointVO vo) {
        boolean deleted = false;
        if (vo != null) {
            for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
                def.beforeDelete(vo.getId());
            deleted = super.delete(vo);
            for (DataPointChangeDefinition def : ModuleRegistry.getDefinitions(DataPointChangeDefinition.class))
                def.afterDelete(vo.getId());
        }
        return deleted;
    }

    @Override
    public void deleteRelationalData(DataPointVO vo) {
        ejt.update("delete from eventHandlersMapping where eventTypeName=? and eventTypeRef1 = " + vo.getId(),
                new Object[] { EventType.EventTypeNames.DATA_POINT });
        ejt.update("delete from userComments where commentType=2 and typeKey = " + vo.getId());
        ejt.update("delete from eventDetectors where dataPointId = " + vo.getId());
        RoleDao.getInstance().deleteRolesForVoPermission(vo, PermissionService.READ);
        RoleDao.getInstance().deleteRolesForVoPermission(vo, PermissionService.SET);
    }

    /**
     * Count the data points on a data source, used for licensing
     * @param dataSourceType
     * @return
     */
    public int countPointsForDataSourceType(String dataSourceType) {
        return ejt.queryForInt("SELECT count(DISTINCT dp.id) FROM dataPoints dp LEFT JOIN dataSources ds ON dp.dataSourceId=ds.id "
                + "WHERE ds.dataSourceType=?", new Object[] { dataSourceType }, 0);
    }

    /**
     * Get a summary of a data point
     * @param xid
     * @return
     */
    public DataPointSummary getSummary(String xid) {
        Select<?> query = this.joinTables(this.create.select(this.table.getIdAlias(),
                this.table.getXidAlias(),
                this.table.getNameAlias(),
                this.table.getAlias("dataSourceId"),
                this.table.getAlias("deviceName"))
                .from(this.table.getTableAsAlias()), null).where(this.table.getXidAlias().eq(xid)).limit(1);

        String sql = query.getSQL();
        List<Object> args = query.getBindValues();
        DataPointSummary item = this.ejt.query(sql, args.toArray(new Object[args.size()]),
                (rs) -> {
                    DataPointSummary summary = new DataPointSummary();
                    summary.setId(rs.getInt(1));
                    summary.setXid(rs.getString(2));
                    summary.setName(rs.getString(3));
                    summary.setDataSourceId(rs.getInt(4));
                    summary.setDeviceName(rs.getString(5));
                    return summary;
                });
        if (item != null) {
            item.setReadRoles(RoleDao.getInstance().getRoles(item.getId(), DataPointVO.class.getSimpleName(), PermissionService.READ));
            item.setSetRoles(RoleDao.getInstance().getRoles(item.getId(), DataPointVO.class.getSimpleName(), PermissionService.SET));
        }
        return item;
    }

    //
    //
    // Event detectors
    //

    /**
     *
     * Loads the event detectors from the database and sets them on the data point.
     *
     * @param dp
     */
    public void loadEventDetectors(DataPointVO dp) {
        dp.setEventDetectors(EventDetectorDao.getInstance().getWithSource(dp.getId(), dp));
    }

    private void saveEventDetectors(DataPointVO dp) {
        // Get the ids of the existing detectors for this point.
        final List<AbstractPointEventDetectorVO<?>> existingDetectors = EventDetectorDao.getInstance().getWithSource(dp.getId(), dp);

        // Insert or update each detector in the point.
        for (AbstractPointEventDetectorVO<?> ped : dp.getEventDetectors()) {
            ped.setSourceId(dp.getId());
            if (ped.getId() > 0){
                //Remove from list
                AbstractPointEventDetectorVO<?> existing = removeFromList(existingDetectors, ped.getId());
                EventDetectorDao.getInstance().update(existing, ped);
            } else {
                ped.setId(Common.NEW_ID);
                EventDetectorDao.getInstance().insert(ped);
            }
        }

        // Delete detectors for any remaining ids in the list of existing
        // detectors.
        for (AbstractEventDetectorVO<?> ed : existingDetectors) {
            EventDetectorDao.getInstance().delete(ed);
        }
    }

    private AbstractPointEventDetectorVO<?> removeFromList(List<AbstractPointEventDetectorVO<?>> list, int id) {
        for (AbstractPointEventDetectorVO<?> ped : list) {
            if (ped.getId() == id) {
                list.remove(ped);
                return ped;
            }
        }
        return null;
    }

    //
    //
    // Point comments
    //
    private static final String POINT_COMMENT_SELECT = UserCommentDao.USER_COMMENT_SELECT
            + "where uc.commentType= " + UserCommentVO.TYPE_POINT + " and uc.typeKey=? " + "order by uc.ts";

    /**
     * Loads the comments from the database and them on the data point.
     *
     * @param dp
     */
    private void loadPointComments(DataPointVO dp) {
        dp.setComments(query(POINT_COMMENT_SELECT, new Object[] { dp.getId() }, UserCommentDao.getInstance().getRowMapper()));
    }


    /**
     * Get the count of all point values for all points
     *
     * @return
     */
    public List<PointHistoryCount> getTopPointHistoryCounts() {
        if (Common.databaseProxy.getNoSQLProxy() == null)
            return this.getTopPointHistoryCountsSql();
        return this.getTopPointHistoryCountsNoSql();
    }

    /**
     * NoSQL version to count point values for each point
     * @return
     */
    private List<PointHistoryCount> getTopPointHistoryCountsNoSql() {

        PointValueDao dao = Common.databaseProxy.newPointValueDao();
        //For now we will do this the slow way
        List<DataPointVO> points = query(getJoinedSelectQuery().getSQL() + " ORDER BY dp.deviceName, dp.name", getListResultSetExtractor());
        List<PointHistoryCount> counts = new ArrayList<>();
        for (DataPointVO point : points) {
            PointHistoryCount phc = new PointHistoryCount();
            long count = dao.dateRangeCount(point.getId(), 0L, Long.MAX_VALUE);
            phc.setCount((int) count);
            phc.setPointId(point.getId());
            phc.setPointName(point.getName());
            counts.add(phc);
        }
        Collections.sort(counts, new Comparator<PointHistoryCount>() {

            @Override
            public int compare(PointHistoryCount count1, PointHistoryCount count2) {
                return count2.getCount() - count1.getCount();
            }

        });

        return counts;
    }

    /**
     * SQL version to count point values for each point
     * @return
     */
    private List<PointHistoryCount> getTopPointHistoryCountsSql() {
        List<PointHistoryCount> counts = query(
                "select dataPointId, count(*) from pointValues group by dataPointId order by 2 desc",
                new RowMapper<PointHistoryCount>() {
                    @Override
                    public PointHistoryCount mapRow(ResultSet rs, int rowNum) throws SQLException {
                        PointHistoryCount c = new PointHistoryCount();
                        c.setPointId(rs.getInt(1));
                        c.setCount(rs.getInt(2));
                        return c;
                    }
                });

        List<DataPointVO> points = query(getJoinedSelectQuery().getSQL() + " ORDER BY deviceName, name", getListResultSetExtractor());

        // Collate in the point names.
        for (PointHistoryCount c : counts) {
            for (DataPointVO point : points) {
                if (point.getId() == c.getPointId()) {
                    c.setPointName(point.getExtendedName());
                    break;
                }
            }
        }

        // Remove the counts for which there are no point, i.e. deleted.
        Iterator<PointHistoryCount> iter = counts.iterator();
        while (iter.hasNext()) {
            PointHistoryCount c = iter.next();
            if (c.getPointName() == null)
                iter.remove();
        }

        return counts;
    }

    /**
     * Get the count of data points per type of data source
     * @return
     */
    public List<DataPointUsageStatistics> getUsage() {
        return ejt.query("SELECT ds.dataSourceType, COUNT(ds.dataSourceType) FROM dataPoints as dp LEFT JOIN dataSources ds ON dp.dataSourceId=ds.id GROUP BY ds.dataSourceType",
                new RowMapper<DataPointUsageStatistics>() {
            @Override
            public DataPointUsageStatistics mapRow(ResultSet rs, int rowNum) throws SQLException {
                DataPointUsageStatistics usage = new DataPointUsageStatistics();
                usage.setDataSourceType(rs.getString(1));
                usage.setCount(rs.getInt(2));
                return usage;
            }
        });
    }

    @Override
    protected String getXidPrefix() {
        return DataPointVO.XID_PREFIX;
    }

    @Override
    protected Object[] voToObjectArray(DataPointVO vo) {
        return new Object[] {
                vo.getXid(),
                vo.getName(),
                SerializationHelper.writeObjectToArray(vo),
                vo.getDataSourceId(),
                vo.getDeviceName(),
                boolToChar(vo.isEnabled()),
                vo.getLoggingType(),
                vo.getIntervalLoggingPeriodType(),
                vo.getIntervalLoggingPeriod(),
                vo.getIntervalLoggingType(),
                vo.getTolerance(),
                boolToChar(vo.isPurgeOverride()),
                vo.getPurgeType(),
                vo.getPurgePeriod(),
                vo.getDefaultCacheSize(),
                boolToChar(vo.isDiscardExtremeValues()),
                vo.getEngineeringUnits(),
                vo.getRollup(),
                vo.getPointLocator().getDataTypeId(),
                boolToChar(vo.getPointLocator().isSettable())};
    }

    //TODO Mango 4.0 should we re-add this?
    protected List<Index> getIndexes() {
        List<Index> indexes = new ArrayList<Index>();
        List<QueryAttribute> columns = new ArrayList<QueryAttribute>();
        //Data Source Name Force
        columns.add(new QueryAttribute("name", new HashSet<String>(), Types.VARCHAR));
        indexes.add(new Index("nameIndex", "ds", columns, "ASC"));

        //Data Source xid Force
        columns = new ArrayList<QueryAttribute>();
        columns.add(new QueryAttribute("xid", new HashSet<String>(), Types.VARCHAR));
        indexes.add(new Index("dataSourcesUn1", "ds", columns, "ASC"));

        //DeviceNameName Index Force
        columns = new ArrayList<QueryAttribute>();
        columns.add(new QueryAttribute("deviceName", new HashSet<String>(), Types.VARCHAR));
        columns.add(new QueryAttribute("name", new HashSet<String>(), Types.VARCHAR));
        indexes.add(new Index("deviceNameNameIndex", "dp", columns, "ASC"));

        //xid point name force
        columns = new ArrayList<QueryAttribute>();
        columns.add(new QueryAttribute("xid", new HashSet<String>(), Types.VARCHAR));
        columns.add(new QueryAttribute("name", new HashSet<String>(), Types.VARCHAR));
        indexes.add(new Index("xidNameIndex", "dp", columns, "ASC"));

        return indexes;
    }

    @Override
    public RowMapper<DataPointVO> getRowMapper() {
        return new DataPointMapper();
    }

    public static class DataPointMapper implements RowMapper<DataPointVO> {
        @Override
        public DataPointVO mapRow(ResultSet rs, int rowNum) throws SQLException {
            int i = 0;
            int id = (rs.getInt(++i));
            String xid = rs.getString(++i);
            String name = rs.getString(++i);

            DataPointVO dp = (DataPointVO) SerializationHelper.readObjectInContext(rs.getBinaryStream(++i));

            dp.setId(id);
            dp.setXid(xid);
            dp.setName(name);
            dp.setDataSourceId(rs.getInt(++i));
            dp.setDeviceName(rs.getString(++i));
            dp.setEnabled(charToBool(rs.getString(++i)));
            dp.setLoggingType(rs.getInt(++i));
            dp.setIntervalLoggingPeriodType(rs.getInt(++i));
            dp.setIntervalLoggingPeriod(rs.getInt(++i));
            dp.setIntervalLoggingType(rs.getInt(++i));
            dp.setTolerance(rs.getDouble(++i));
            dp.setPurgeOverride(charToBool(rs.getString(++i)));
            dp.setPurgeType(rs.getInt(++i));
            dp.setPurgePeriod(rs.getInt(++i));
            dp.setDefaultCacheSize(rs.getInt(++i));
            dp.setDiscardExtremeValues(charToBool(rs.getString(++i)));
            dp.setEngineeringUnits(rs.getInt(++i));
            dp.setRollup(rs.getInt(++i));

            // read and discard dataTypeId
            rs.getInt(++i);
            // read and discard settable boolean
            rs.getString(++i);

            // Data source information from join
            dp.setDataSourceName(rs.getString(++i));
            dp.setDataSourceXid(rs.getString(++i));
            dp.setDataSourceTypeName(rs.getString(++i));

            dp.ensureUnitsCorrect();
            return dp;
        }
    }

    /**
     * Loads the event detectors, point comments and tags
     * @param vo
     */
    public void loadPartialRelationalData(DataPointVO vo) {
        this.loadEventDetectors(vo);
        this.loadPointComments(vo);
        vo.setTags(DataPointTagsDao.getInstance().getTagsForDataPointId(vo.getId()));
    }

    private void loadPartialRelationalData(List<DataPointVO> dps) {
        for (DataPointVO dp : dps) {
            loadPartialRelationalData(dp);
        }
    }

    /**
     * Loads the event detectors, point comments, tags data source and template name
     * Used by getFull()
     * @param vo
     */
    @Override
    public void loadRelationalData(DataPointVO vo) {
        this.loadPartialRelationalData(vo);
        this.loadDataSource(vo);
        //Populate permissions
        vo.setReadRoles(RoleDao.getInstance().getRoles(vo, PermissionService.READ));
        vo.setSetRoles(RoleDao.getInstance().getRoles(vo, PermissionService.SET));
        vo.setDataSourceEditRoles(RoleDao.getInstance().getRoles(vo.getDataSourceId(), DataSourceVO.class.getSimpleName(), PermissionService.EDIT));
    }

    @Override
    public void saveRelationalData(DataPointVO vo, boolean insert) {
        saveEventDetectors(vo);

        Map<String, String> tags = vo.getTags();
        if (tags == null) {
            if (!insert) {
                // only delete the name and device tags, leave existing tags intact
                DataPointTagsDao.getInstance().deleteNameAndDeviceTagsForDataPointId(vo.getId());
            }
            tags = Collections.emptyMap();
        } else if (!insert) {
            // we only need to delete tags when doing an update
            DataPointTagsDao.getInstance().deleteTagsForDataPointId(vo.getId());
        }

        DataPointTagsDao.getInstance().insertTagsForDataPoint(vo, tags);
        //Replace the role mappings
        RoleDao.getInstance().replaceRolesOnVoPermission(vo.getReadRoles(), vo, PermissionService.READ, insert);
        RoleDao.getInstance().replaceRolesOnVoPermission(vo.getSetRoles(), vo, PermissionService.SET, insert);
    }

    /**
     * Load the datasource info into the DataPoint
     *
     * @param vo
     * @return
     */
    public void loadDataSource(DataPointVO vo) {
        //TODO Mango 4.0 get the sourceTypeName in the JOIN, then remove this crap
        //Get the values from the datasource table
        //TODO Could speed this up if necessary...
        DataSourceVO<?> dsVo = DataSourceDao.getInstance().get(vo.getDataSourceId());
        vo.setDataSourceName(dsVo.getName());
        vo.setDataSourceTypeName(dsVo.getDefinition().getDataSourceTypeName());
        vo.setDataSourceXid(dsVo.getXid());

    }

    /**
     * Gets all data points that a user has access to (i.e. readPermission, setPermission or the datasource editPermission).
     * For a superadmin user it will get all data points in the system.
     *
     * @param user
     * @return
     */
    public List<DataPointVO> dataPointsForUser(User user) {
        List<DataPointVO> result = new ArrayList<>();
        dataPointsForUser(user, (item, index) -> result.add(item));
        return result;
    }

    /**
     * Gets all data points that a user has access to (i.e. readPermission, setPermission or the datasource editPermission).
     * For a superadmin user it will get all data points in the system.
     *
     * @param user
     * @param callback
     */
    public void dataPointsForUser(User user, MappedRowCallback<DataPointVO> callback) {
        dataPointsForUser(user, callback, null, null, null);
    }

    /**
     * Gets all data points that a user has access to (i.e. readPermission, setPermission or the datasource editPermission).
     * For a superadmin user it will get all data points in the system.
     *
     * @param user
     * @param callback
     * @param sort (may be null)
     * @param limit (may be null)
     * @param offset (may be null)
     */
    public void dataPointsForUser(User user, MappedRowCallback<DataPointVO> callback, List<SortField<Object>> sort, Integer limit, Integer offset) {
        Condition condition = null;
        //TODO Mango 4.0 fix this
        if (!user.hasAdminRole()) {
            //condition = this.userHasPermission(user);
        }
        SelectJoinStep<Record> select = this.create.select(getSelectFields()).from(this.table.getTableAsAlias());
        this.customizedQuery(select, condition, sort, limit, offset, callback);
    }

    /**
     * Gets data points for a set of tags that a user has access to (i.e. readPermission, setPermission or the datasource editPermission).
     *
     * @param restrictions
     * @param user
     * @return
     */
    public List<DataPointVO> dataPointsForTags(Map<String, String> restrictions, User user) {
        List<DataPointVO> result = new ArrayList<>();
        dataPointsForTags(restrictions, user, (item, index) -> result.add(item));
        return result;
    }

    /**
     * Gets data points for a set of tags that a user has access to (i.e. readPermission, setPermission or the datasource editPermission).
     *
     * @param restrictions
     * @param user
     * @param callback
     */
    public void dataPointsForTags(Map<String, String> restrictions, User user, MappedRowCallback<DataPointVO> callback) {
        dataPointsForTags(restrictions, user, callback, null, null, null);
    }

    /**
     * Gets data points for a set of tags that a user has access to (i.e. readPermission, setPermission or the datasource editPermission).
     *
     * @param restrictions
     * @param user
     * @param callback
     * @param sort (may be null)
     * @param limit (may be null)
     * @param offset (may be null)
     */
    public void dataPointsForTags(Map<String, String> restrictions, User user, MappedRowCallback<DataPointVO> callback, List<SortField<Object>> sort, Integer limit, Integer offset) {
        if (restrictions.isEmpty()) {
            throw new IllegalArgumentException("restrictions should not be empty");
        }

        Map<String, Name> tagKeyToColumn = DataPointTagsDao.getInstance().tagKeyToColumn(restrictions.keySet());

        List<Condition> conditions = restrictions.entrySet().stream().map(e -> {
            return DSL.field(DATA_POINT_TAGS_PIVOT_ALIAS.append(tagKeyToColumn.get(e.getKey()))).eq(e.getValue());
        }).collect(Collectors.toCollection(ArrayList::new));

        //TODO Mango 4.0 fix this
        if (!user.hasAdminRole()) {
            //conditions.add(this.userHasPermission(user));
        }

        Table<Record> pivotTable = DataPointTagsDao.getInstance().createTagPivotSql(tagKeyToColumn).asTable().as(DATA_POINT_TAGS_PIVOT_ALIAS);

        SelectOnConditionStep<Record> select = this.create.select(getSelectFields()).from(this.table.getTableAsAlias()).leftJoin(pivotTable)
                .on(DataPointTagsDao.PIVOT_ALIAS_DATA_POINT_ID.eq(ID));

        this.customizedQuery(select, DSL.and(conditions), sort, limit, offset, callback);
    }

    //TODO  Mango 4.0 fix this
    public Condition userHasPermission(User user) {
        Set<String> userPermissions = new HashSet<>();
        List<Condition> conditions = new ArrayList<>(userPermissions.size() * 3);

        for (String userPermission : userPermissions) {
            conditions.add(fieldMatchesUserPermission(READ_PERMISSION, userPermission));
            conditions.add(fieldMatchesUserPermission(SET_PERMISSION, userPermission));
            conditions.add(fieldMatchesUserPermission(DataSourceDao.EDIT_PERMISSION, userPermission));
        }

        return DSL.or(conditions);
    }

    //TODO Mango 4.0 fix this
    public Condition userHasSetPermission(User user) {
        Set<String> userPermissions = new HashSet<>();
        List<Condition> conditions = new ArrayList<>(userPermissions.size() * 2);

        for (String userPermission : userPermissions) {
            conditions.add(fieldMatchesUserPermission(SET_PERMISSION, userPermission));
            conditions.add(fieldMatchesUserPermission(DataSourceDao.EDIT_PERMISSION, userPermission));
        }

        return DSL.or(conditions);
    }

    //TODO Mango 4.0 fix this
    public Condition userHasEditPermission(User user) {
        Set<String> userPermissions = new HashSet<>();
        List<Condition> conditions = new ArrayList<>(userPermissions.size());

        for (String userPermission : userPermissions) {
            conditions.add(fieldMatchesUserPermission(DataSourceDao.EDIT_PERMISSION, userPermission));
        }

        return DSL.or(conditions);
    }

    @Override
    public List<Field<?>> getSelectFields() {
        List<Field<?>> fields = new ArrayList<>(this.table.getSelectFields());
        fields.add(dataSourceTable.getAlias("name"));
        fields.add(dataSourceTable.getAlias("xid"));
        fields.add(dataSourceTable.getAlias("dataSourceType"));
        return fields;
    }

    @Override
    public <R extends Record> SelectJoinStep<R> joinTables(SelectJoinStep<R> select, ConditionSortLimit conditions) {
        select = select.join(dataSourceTable.getTableAsAlias())
                .on(DSL.field(dataSourceTable.getAlias("id"))
                        .eq(this.table.getAlias("dataSourceId")));
        if (conditions instanceof ConditionSortLimitWithTagKeys) {
            Map<String, Name> tagKeyToColumn = ((ConditionSortLimitWithTagKeys) conditions).getTagKeyToColumn();
            if (!tagKeyToColumn.isEmpty()) {
                Table<Record> pivotTable = DataPointTagsDao.getInstance().createTagPivotSql(tagKeyToColumn).asTable().as(DATA_POINT_TAGS_PIVOT_ALIAS);

                return select.leftJoin(pivotTable)
                        .on(DataPointTagsDao.PIVOT_ALIAS_DATA_POINT_ID.eq(ID));
            }
        }
        return select;
    }

    @Override
    public void customizedQuery(SelectJoinStep<Record> select, Condition condition,
            List<SortField<Object>> sort, Integer limit, Integer offset,
            MappedRowCallback<DataPointVO> callback) {
        if (condition instanceof ConditionSortLimitWithTagKeys) {
            Map<String, Name> tagKeyToColumn = ((ConditionSortLimitWithTagKeys) condition).getTagKeyToColumn();
            if (!tagKeyToColumn.isEmpty()) {
                Table<Record> pivotTable = DataPointTagsDao.getInstance().createTagPivotSql(tagKeyToColumn).asTable().as(DATA_POINT_TAGS_PIVOT_ALIAS);

                select = select.leftJoin(pivotTable)
                        .on(DataPointTagsDao.PIVOT_ALIAS_DATA_POINT_ID.eq(ID));
            }
        }
        SelectConnectByStep<Record> afterWhere = condition == null ? select : select.where(condition);
        SelectLimitStep<Record> afterSort = sort == null ? afterWhere : afterWhere.orderBy(sort);

        Select<Record> offsetStep = afterSort;
        if (limit != null) {
            if (offset != null) {
                offsetStep = afterSort.limit(offset, limit);
            } else {
                offsetStep = afterSort.limit(limit);
            }
        }

        String sql = offsetStep.getSQL();
        List<Object> arguments = offsetStep.getBindValues();
        Object[] argumentsArray = arguments.toArray(new Object[arguments.size()]);

        LogStopWatch stopWatch = null;
        if (useMetrics) {
            stopWatch = new LogStopWatch();
        }

        this.query(sql, argumentsArray, getCallbackResultSetExtractor(callback));

        if (stopWatch != null) {
            stopWatch.stop("customizedQuery(): " + this.create.renderInlined(offsetStep), metricsThreshold);
        }
    }

    @Override
    protected RQLToCondition createRqlToCondition() {
        // we create one every time as they are stateful for this DAO
        return null;
    }

    @Override
    public ConditionSortLimitWithTagKeys rqlToCondition(ASTNode rql) {
        // RQLToConditionWithTagKeys is stateful, we need to create a new one every time
        RQLToConditionWithTagKeys rqlToSelect = new RQLToConditionWithTagKeys(this.table.getAliasMap(), this.valueConverterMap);
        return rqlToSelect.visit(rql);
    }

    @Override
    protected RQLToCondition createRqlToCondition(Map<String, Field<?>> fieldMap,
            Map<String, Function<Object, Object>> converterMap) {
        return new RQLToConditionWithTagKeys(fieldMap, converterMap);
    }

    public static final String PERMISSION_START_REGEX = "(^|[,])\\s*";
    public static final String PERMISSION_END_REGEX = "\\s*($|[,])";

    Condition fieldMatchesUserPermission(Field<String> field, String userPermission) {
        return DSL.or(
                field.eq(userPermission),
                DSL.and(
                        field.isNotNull(),
                        field.notEqual(""),
                        field.likeRegex(PERMISSION_START_REGEX + userPermission + PERMISSION_END_REGEX)
                        )
                );
    }

    protected void notifyTagsUpdated(DataPointVO dataPoint) {
        this.eventPublisher.publishEvent(new DataPointTagsUpdatedEvent(this, dataPoint));
    }

    @Override
    protected Map<String, Function<Object, Object>> createValueConverterMap() {
        Map<String, Function<Object, Object>> map = new HashMap<>(super.createValueConverterMap());
        map.put("dataTypeId", value -> {
            if (value instanceof String) {
                return DataTypes.CODES.getId((String) value);
            }
            return value;
        });
        return map;
    }

    @Override
    protected ResultSetExtractor<List<DataPointVO>> getListResultSetExtractor() {
        return getListResultSetExtractor((e, rs) -> {
            if (e.getCause() instanceof ModuleNotLoadedException) {
                try {
                    LOG.error("Data point with xid '" + rs.getString("xid")
                    + "' could not be loaded. Is its module missing?", e.getCause());
                }catch(SQLException e1) {
                    LOG.error(e.getMessage(), e);
                }
            }else {
                LOG.error(e.getMessage(), e);
            }
        });
    }

    @Override
    protected ResultSetExtractor<Void> getCallbackResultSetExtractor(
            MappedRowCallback<DataPointVO> callback) {
        return getCallbackResultSetExtractor(callback, (e, rs) -> {
            if (e.getCause() instanceof ModuleNotLoadedException) {
                try {
                    LOG.error("Data point with xid '" + rs.getString("xid")
                    + "' could not be loaded. Is its module missing?", e.getCause());
                }catch(SQLException e1) {
                    LOG.error(e.getMessage(), e);
                }
            }else {
                LOG.error(e.getMessage(), e);
            }
        });
    }

}
