/**
 * Copyright (C) 2016 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.vo.json;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.spi.JsonProperty;
import com.serotonin.json.spi.JsonSerializable;
import com.serotonin.json.type.JsonObject;
import com.serotonin.m2m2.db.dao.JsonDataDao;
import com.serotonin.m2m2.i18n.TranslatableJsonException;
import com.serotonin.m2m2.vo.AbstractVO;
import com.serotonin.m2m2.vo.RoleVO;

/**
 * @author Terry Packer
 *
 */
public class JsonDataVO extends AbstractVO<JsonDataVO> implements Serializable, JsonSerializable {
	
	public static final String XID_PREFIX = "JSON_";
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private Object jsonData;

    @JsonProperty
    private Set<RoleVO> readRoles = Collections.emptySet();
    @JsonProperty
    private Set<RoleVO> editRoles = Collections.emptySet();
	@JsonProperty
	private boolean publicData;
	
	public Object getJsonData() {
		return jsonData;
	}
	public void setJsonData(Object data) {
		this.jsonData = data;
	}

	public Set<RoleVO> getReadRoles() {
        return readRoles;
    }
    public void setReadRoles(Set<RoleVO> readRoles) {
        this.readRoles = readRoles;
    }
    public Set<RoleVO> getEditRoles() {
        return editRoles;
    }
    public void setEditRoles(Set<RoleVO> editRoles) {
        this.editRoles = editRoles;
    }
    public boolean isPublicData() {
		return publicData;
	}
	public void setPublicData(boolean publicData){
		this.publicData = publicData;
	}

    @Override
    public void jsonWrite(ObjectWriter writer) throws IOException, JsonException {
        super.jsonWrite(writer);
        writer.writeEntry("jsonData", JsonDataDao.getInstance().writeValueAsString(jsonData));
    }
    @Override
    public void jsonRead(JsonReader reader, JsonObject jsonObject) throws JsonException {
    	super.jsonRead(reader, jsonObject);
    	String json = jsonObject.getString("jsonData");
    	try{
    		jsonData = JsonDataDao.getInstance().readValueFromString(json);
    	}catch(Exception e){
            throw new TranslatableJsonException("emport.error.parseError", "jsonData");
    	}
    }
	
	@Override
	public String getTypeKey() {
		return "event.audit.jsonData";
	}

	@Override
	protected JsonDataDao getDao() {
		return JsonDataDao.getInstance();
	}

}
