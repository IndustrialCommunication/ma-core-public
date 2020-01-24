/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.infiniteautomation.mango.spring.service;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.infiniteautomation.mango.spring.db.JsonDataTableDefinition;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.JsonDataDao;
import com.serotonin.m2m2.module.definitions.permissions.JsonDataCreatePermissionDefinition;
import com.serotonin.m2m2.vo.json.JsonDataVO;
import com.serotonin.m2m2.vo.role.Role;

/**
 * @author Terry Packer
 *
 */
public class JsonDataServiceTest extends AbstractVOServiceWithPermissionsTest<JsonDataVO, JsonDataTableDefinition, JsonDataDao, JsonDataService>{

    @Override
    String getCreatePermissionType() {
        return JsonDataCreatePermissionDefinition.TYPE_NAME;
    }

    @Override
    void setReadRoles(Set<Role> roles, JsonDataVO vo) {
        vo.setReadRoles(roles);
    }

    @Override
    void setEditRoles(Set<Role> roles, JsonDataVO vo) {
        vo.setEditRoles(roles);
    }

    @Override
    JsonDataService getService() {
        return Common.getBean(JsonDataService.class);
    }

    @Override
    JsonDataDao getDao() {
        return JsonDataDao.getInstance();
    }

    @Override
    void assertVoEqual(JsonDataVO expected, JsonDataVO actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getXid(), actual.getXid());
        assertEquals(expected.getName(), actual.getName());

        //TODO assert json value
    }

    @Override
    JsonDataVO newVO() {
        JsonDataVO vo = new JsonDataVO();
        vo.setXid(UUID.randomUUID().toString());
        vo.setName(UUID.randomUUID().toString());
        Map<String, String> jsonData = new HashMap<>();
        jsonData.put("test", "value");
        vo.setJsonData(jsonData);
        return vo;
    }

    @Override
    JsonDataVO updateVO(JsonDataVO existing) {
        JsonDataVO copy = existing.copy();
        Map<String, String> jsonData = new HashMap<>();
        jsonData.put("test", "value-updated");
        copy.setJsonData(jsonData);
        return copy;
    }

}
