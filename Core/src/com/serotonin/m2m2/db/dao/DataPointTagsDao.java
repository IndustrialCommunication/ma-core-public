/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.BatchBindStep;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Record2;
import org.jooq.Select;
import org.jooq.SelectConditionStep;
import org.jooq.SelectJoinStep;
import org.jooq.SelectOnConditionStep;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import com.infiniteautomation.mango.db.query.ConditionSortLimit;
import com.infiniteautomation.mango.db.query.ConditionSortLimitWithTagKeys;
import com.infiniteautomation.mango.db.query.RQLToConditionWithTagKeys;
import com.infiniteautomation.mango.db.tables.DataPointTags;
import com.infiniteautomation.mango.db.tables.DataPoints;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.LazyInitializer;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.DatabaseProxy;
import com.serotonin.m2m2.rt.dataImage.DataPointRT;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.permission.PermissionHolder;

import net.jazdw.rql.parser.ASTNode;

/**
 * @author Jared Wiltshire
 */
@Repository()
public class DataPointTagsDao extends BaseDao {
    private static final LazyInitializer<DataPointTagsDao> springInstance = new LazyInitializer<>();

    public static final String DEVICE_TAG_KEY = "device";
    public static final String NAME_TAG_KEY = "name";

    private final DataPointTags table = DataPointTags.DATA_POINT_TAGS;
    private final DataPoints dataPointTable = DataPoints.DATA_POINTS;
    private final PermissionService permissionService;

    @Autowired
    private DataPointTagsDao(PermissionService permissionService, DatabaseProxy databaseProxy) {
        super(databaseProxy);
        this.permissionService = permissionService;
    }

    /**
     * Get cached instance from Spring Context
     */
    public static DataPointTagsDao getInstance() {
        return springInstance.get(() -> Common.getRuntimeContext().getBean(DataPointTagsDao.class));
    }

    /**
     * Retrieves all tag keys and values from the database for a datapoint.
     * Contains a "name" and "device" key as opposed to the tags retrieved via DataPointVO.getTags().
     *
     */
    public Map<String, String> getTagsForDataPointId(int dataPointId) {
        Select<Record2<String, String>> query = this.create.select(table.tagKey, table.tagValue)
                .from(table)
                .where(table.dataPointId.eq(dataPointId));

        try (Stream<Record2<String, String>> stream = query.stream()) {
            return stream.collect(Collectors.toMap(Record2::value1, Record2::value2));
        }
    }

    public int deleteTagsForDataPointId(int dataPointId) {
        return this.create.deleteFrom(table)
                .where(table.dataPointId.eq(dataPointId))
                .execute();
    }

    /**
     * Inserts tags into the database for a DataPointVO. Also inserts the "name" and "device" tags from the data point properties.
     *
     */
    public void insertTagsForDataPoint(DataPointVO dataPoint) {
        Map<String, String> tags = dataPoint.getTags();
        if (tags == null) throw new IllegalArgumentException("Tags cannot be null");
        if (tags.containsKey(NAME_TAG_KEY)) throw new IllegalArgumentException("Tags cannot contain 'name'");
        if (tags.containsKey(DEVICE_TAG_KEY)) throw new IllegalArgumentException("Tags cannot contain 'deviceName'");

        int dataPointId = dataPoint.getId();
        String name = dataPoint.getName();
        String deviceName = dataPoint.getDeviceName();

        BatchBindStep b = create.batch(
                DSL.insertInto(table)
                .columns(table.dataPointId, table.tagKey, table.tagValue)
                .values((Integer) null, null, null));
        tags.forEach((key, value) -> b.bind(dataPointId, key, value));

        if (name != null && !name.isEmpty()) {
            b.bind(dataPointId, NAME_TAG_KEY, name);
        }
        if (deviceName != null && !deviceName.isEmpty()) {
            b.bind(dataPointId, DEVICE_TAG_KEY, deviceName);
        }

        b.execute();
    }

    public void updateTags(DataPointVO dataPoint) {
        Map<String, String> tags = dataPoint.getTags();
        if (tags == null) throw new IllegalArgumentException("Tags cannot be null");
        if (tags.containsKey(NAME_TAG_KEY)) throw new IllegalArgumentException("Tags cannot contain 'name'");
        if (tags.containsKey(DEVICE_TAG_KEY)) throw new IllegalArgumentException("Tags cannot contain 'device'");

        Map<String, String> allTags = new HashMap<>(tags);
        if (dataPoint.getName() != null && !dataPoint.getName().isEmpty()) {
            allTags.put(NAME_TAG_KEY, dataPoint.getName());
        }
        if (dataPoint.getDeviceName() != null && !dataPoint.getDeviceName().isEmpty()) {
            allTags.put(DEVICE_TAG_KEY, dataPoint.getDeviceName());
        }

        List<Query> queries = new ArrayList<>(allTags.size() + 3);
        queries.add(DSL.deleteFrom(table).where(table.dataPointId.eq(dataPoint.getId()))
                .and(table.tagKey.notIn(allTags.keySet())));
        for (Entry<String, String> entry : allTags.entrySet()) {
            queries.add(updateTagValue(dataPoint.getId(), entry.getKey(), entry.getValue()));
        }
        create.batch(queries).execute();
    }

    private Query updateTagValue(int dataPointId, String tagKey, String tagValue) {
        switch (create.dialect()) {
            case MYSQL:
            case MARIADB:
                // the @Supports annotation on mergeInto claims that it supports MySQL, however it does not
                // translate/emulate the merge using "on duplicate key update" so it fails
                return DSL.insertInto(table)
                        .columns(table.dataPointId, table.tagKey, table.tagValue)
                        .values(dataPointId, tagKey, tagValue)
                        .onDuplicateKeyUpdate()
                        .set(table.tagValue, tagValue);
            default:
                return DSL.mergeInto(table)
                        .using(DSL.selectOne())
                        .on(table.dataPointId.eq(dataPointId), table.tagKey.eq(tagKey))
                        .whenMatchedThenUpdate()
                        .set(table.tagValue, tagValue)
                        .whenNotMatchedThenInsert(table.dataPointId, table.tagKey, table.tagValue)
                        .values(dataPointId, tagKey, tagValue);
        }
    }

    /**
     * Only to be used when saving data point tags independently from the DataPointVO itself.
     * The DataPointVO tags must not be null.
     *
     */
    public void saveDataPointTags(DataPointVO dataPoint) {
        this.doInTransaction(txStatus -> {
            updateTags(dataPoint);

            DataPointRT rt = Common.runtimeManager.getDataPoint(dataPoint.getId());
            if (rt != null) {
                DataPointVO rtVo = rt.getVO();
                rtVo.setTags(dataPoint.getTags());
            }

            DataPointDao.getInstance().notifyTagsUpdated(dataPoint);
        });
    }


    public Set<String> getTagKeys(PermissionHolder user) {
        SelectJoinStep<Record1<String>> query = this.create.selectDistinct(table.tagKey)
                .from(table);

        if (!permissionService.hasAdminRole(user)) {
            query = query.join(dataPointTable).on(table.dataPointId.eq(dataPointTable.id));

            ConditionSortLimit csl = new ConditionSortLimit(null, null, null, null);
            query = DataPointDao.getInstance().joinPermissions(query, user);
            try (Stream<Record1<String>> stream = query.where(csl.getCondition()).stream()) {
                return stream.map(Record1::value1).collect(Collectors.toSet());
            }
        }else {
            try (Stream<Record1<String>> stream = query.stream()) {
                return stream.map(Record1::value1).collect(Collectors.toSet());
            }
        }
    }

    public Set<String> getTagValuesForKey(String tagKey, PermissionHolder user) {
        SelectJoinStep<Record1<String>> query = this.create.selectDistinct(table.tagValue)
                .from(table);

        SelectConditionStep<Record1<String>> conditional;
        if (!permissionService.hasAdminRole(user)) {
            query = query.join(dataPointTable).on(table.dataPointId.eq(dataPointTable.id));
            ConditionSortLimit csl = new ConditionSortLimit(table.tagKey.eq(tagKey), null, null, null);
            query = DataPointDao.getInstance().joinPermissions(query, user);
            conditional = query.where(csl.getCondition());
        }else {
            conditional = query.where(table.tagKey.eq(tagKey));
        }

        try (Stream<Record1<String>> stream = conditional.stream()) {
            return stream.map(Record1::value1).collect(Collectors.toSet());
        }
    }

    /**
     * For use in Script to get values for a key
     */
    public Set<String> getTagValuesForKey(String tagKey, Map<String, String> restrictions, PermissionHolder user) {
        if (restrictions.isEmpty()) {
            return getTagValuesForKey(tagKey, user);
        }

        Set<String> keys = new HashSet<>(restrictions.keySet());
        keys.add(tagKey);
        Map<String, Field<String>> tagFields = getTagFields(keys);

        List<Condition> conditions = restrictions.entrySet().stream()
                .map(e -> tagFields.get(e.getKey()).eq(e.getValue()))
                .collect(Collectors.toCollection(ArrayList::new));
        Condition allConditions = DSL.and(conditions);

        return getTagValuesForKey(tagKey, tagFields, allConditions, user);
    }

    /**
     * For use with AST node to get values for a key
     */
    public Set<String> getTagValuesForKey(String tagKey, ASTNode restrictions, PermissionHolder user) {
        RQLToConditionWithTagKeys visitor = new RQLToConditionWithTagKeys();
        // ensures that the tagKey we are querying on gets added to the tagKey -> field map
        visitor.getTagField(tagKey);

        List<Condition> conditionList = new ArrayList<>();
        ConditionSortLimitWithTagKeys conditions = visitor.visit(restrictions);
        if (conditions.getCondition() != null) {
            conditionList.add(conditions.getCondition());
        }
        Condition allConditions = DSL.and(conditionList);

        return getTagValuesForKey(tagKey, conditions.getTagFields(), allConditions, user);
    }

    private Set<String> getTagValuesForKey(String tagKey, Map<String, Field<String>> tagFields, Condition allConditions, PermissionHolder user) {
        Field<String> tagField = tagFields.get(tagKey);
        SelectJoinStep<Record1<String>> select = create.selectDistinct(tagField).from(dataPointTable);
        select = joinTags(select, dataPointTable.id, tagFields);

        if (!permissionService.hasAdminRole(user)) {
            select = DataPointDao.getInstance().joinPermissions(select, user);
        }

        Select<Record1<String>> result = select.where(allConditions);
        try (Stream<Record1<String>> stream = result.stream()) {
            return stream.map(Record1::value1).collect(Collectors.toSet());
        }
    }

    /**
     * This method does not filter the tags based on the data point permissions. It should only be used
     * when joining onto the data points table (the filtering happens there post-join).
     *
     * @param select table to join onto
     * @param pointIdField data point id field to join on
     * @param tagFields map of tag key to field
     * @param joinConditions additional conditions to add to join on clause
     * @return joined table
     */
    public <R extends Record> SelectJoinStep<R> joinTags(SelectJoinStep<R> select, Field<Integer> pointIdField, Map<String, Field<String>> tagFields, Condition... joinConditions) {
        // left join tags table once per tag key
        for (Map.Entry<String, Field<String>> entry : tagFields.entrySet()) {
            Field<String> tagField = entry.getValue();
            DataPointTags tagsAs = table.as(tagField.getQualifiedName().qualifier());
            SelectOnConditionStep<R> onConditionStep = select.leftOuterJoin(tagsAs)
                    .on(pointIdField.eq(tagsAs.dataPointId), tagsAs.tagKey.eq(entry.getKey()));
            // prevents "and true" being added to rendered SQL when there are no additional conditions
            select = joinConditions.length > 0 ? onConditionStep.and(DSL.and(joinConditions)) : onConditionStep;
        }
        return select;
    }

    /**
     * Maps tag keys to generic keyX to prevent SQL injection
     */
    Map<String, Field<String>> getTagFields(Set<String> tagKeys) {
        int i = 0;
        Map<String, Field<String>> tagFields = new HashMap<>(tagKeys.size());
        for (String key : tagKeys) {
            tagFields.put(key, DataPointTags.DATA_POINT_TAGS.as("key" + i++).tagValue);
        }
        return tagFields;
    }
}
