/**
 * Copyright (C) 2014 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.rt.script;

/**
 * @author Terry Packer
 *
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.List;

import com.serotonin.json.JsonException;
import com.serotonin.json.ObjectWriter;
import com.serotonin.json.type.JsonArray;
import com.serotonin.json.type.JsonBoolean;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonString;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.i18n.TranslatableJsonException;
import com.serotonin.m2m2.vo.DataPointVO;

/**
 * Holds the information for a variable in the script context
 * 
 * @author Terry Packer
 *
 */
public class ScriptContextVariable implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private int dataPointId;
	private String variableName;
	private boolean contextUpdate; //Do we update the context?
	
	
	
	/**
	 * @param dataPointId
	 * @param variableName
	 * @param contextUpdate
	 */
	public ScriptContextVariable(int dataPointId, String variableName,
			boolean contextUpdate) {
		super();
		this.dataPointId = dataPointId;
		this.variableName = variableName;
		this.contextUpdate = contextUpdate;
	}

	public ScriptContextVariable(){
		
	}

	public int getDataPointId() {
		return dataPointId;
	}

	public void setDataPointId(int dataPointId) {
		this.dataPointId = dataPointId;
	}

	public String getVariableName() {
		return variableName;
	}

	public void setVariableName(String variableName) {
		this.variableName = variableName;
	}

	public boolean isContextUpdate() {
		return contextUpdate;
	}

	public void setContextUpdate(boolean contextUpdate) {
		this.contextUpdate = contextUpdate;
	}
	
	
    public static boolean validateVarName(String varName) {
        char ch = varName.charAt(0);
        if (!Character.isLetter(ch) && ch != '_')
            return false;
        for (int i = 1; i < varName.length(); i++) {
            ch = varName.charAt(i);
            if (!Character.isLetterOrDigit(ch) && ch != '_')
                return false;
        }
        return true;
    }

    public static String contextToString(List<ScriptContextVariable> context) {
        DataPointDao dataPointDao = new DataPointDao();
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ScriptContextVariable ivp : context) {
            DataPointVO dp = dataPointDao.getDataPoint(ivp.getDataPointId());
            if (first){
                first = false;
            }else
                sb.append(", ");
            sb.append("{ name=");
            if (dp == null)
                sb.append("?");
            else
                sb.append(dp.getName()).append(", variableName=");
            sb.append(ivp.getVariableName()).append(", updateContext=").append(ivp.isContextUpdate()).append("}");
        }
        return sb.toString();
    }

    public static void jsonWriteVarContext(ObjectWriter writer, List<ScriptContextVariable> context) throws IOException,
            JsonException {
        DataPointDao dataPointDao = new DataPointDao();
        JsonArray pointList = new JsonArray();
        for (ScriptContextVariable p : context) {
            DataPointVO dp = dataPointDao.getDataPoint(p.getDataPointId());
            if (dp != null) {
                JsonObject point = new JsonObject();
                pointList.add(point);
                point.put("variableName", new JsonString(p.getVariableName()));
                point.put("dataPointXid", new JsonString(dp.getXid()));
                point.put("updateContext", new JsonBoolean(p.isContextUpdate()));
            }
        }
        writer.writeEntry("context", pointList);
    }

    public static void jsonReadVarContext(JsonObject json, List<ScriptContextVariable> context) throws JsonException {
        JsonArray jsonContext = json.getJsonArray("context");
        if (jsonContext != null) {
            context.clear();
            DataPointDao dataPointDao = new DataPointDao();

            for (JsonValue jv : jsonContext) {
                JsonObject jo = jv.toJsonObject();
                String xid = jo.getString("dataPointXid");
                if (xid == null)
                    throw new TranslatableJsonException("emport.error.meta.missing", "dataPointXid");

                DataPointVO dp = dataPointDao.getDataPoint(xid);
                if (dp == null)
                    throw new TranslatableJsonException("emport.error.missingPoint", xid);

                String var = jo.getString("variableName");
                if (var == null){
                	var = jo.getString("varName");
                	if(var == null)
                		 throw new TranslatableJsonException("emport.error.meta.missing", "variableName");
                }
                   
                
                String isContextUpdateString = jo.getString("updateContext");
                boolean isContextUpdate = true;
                if(isContextUpdateString != null)
                	isContextUpdate = Boolean.parseBoolean(isContextUpdateString);

                context.add(new ScriptContextVariable(dp.getId(), var, isContextUpdate));
            }
        }
    }
	
}

