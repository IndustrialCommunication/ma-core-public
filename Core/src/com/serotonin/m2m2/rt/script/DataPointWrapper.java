/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.rt.script;

import java.util.Map;

import javax.measure.unit.Unit;

import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.hierarchy.PointHierarchy;

/**
 * @author Terry Packer
 *
 */
public class DataPointWrapper {
	
	private DataPointVO vo;
	private AbstractPointWrapper wrapper;
	
	public DataPointWrapper(DataPointVO vo, AbstractPointWrapper wrapper){
		this.vo = vo;
		this.wrapper = wrapper;
	}
	
    public String getExtendedName() {
        return DataPointVO.getExtendedName(vo);
    }
    
    public boolean isSettable() {
        return vo.isSettable();
    }

    public String getDeviceName() {
        return vo.getDeviceName();
    }

    public boolean isEnabled() {
        return vo.isEnabled();
    }
    
    public int getId() {
        return vo.getId();
    }
    
    public String getXid() {
        return vo.getXid();
    }
    
    public String getName() {
        return vo.getName();
    }
    
    public String getPath() {
        return PointHierarchy.getFlatPath(vo.getId(), DataPointDao.instance.getPointHierarchy(true).getRoot());
    }
    
    public Unit<?> getUnit(){
    	return vo.getUnit();
    }
    
    public String getDataSourceName() {
        return vo.getDataSourceName();
    }
    
    public String getDataSourceXid() {
        return vo.getDataSourceXid();
    }
    
    public Map<String, String> getTags() {
        return vo.getTags();
    }
    
    public AbstractPointWrapper getRuntime(){
    	return this.wrapper;
    }
    
    public String getHelp(){
    	return toString();
    }
    
    /**
     * For subclass use
     * @param builder
     */
    public void helpImpl(StringBuilder builder){ }
    
	public String toString(){
		StringBuilder builder = new StringBuilder();
		builder.append("{ ");
		builder.append("extendedName: ").append(getExtendedName()).append(",\n");
		builder.append("settable: ").append(isSettable()).append(",\n");
		builder.append("deviceName: ").append(getDeviceName()).append(",\n");
		builder.append("enabled: ").append(isEnabled()).append(",\n");
		builder.append("id: ").append(getId()).append(",\n");
		builder.append("xid: ").append(getXid()).append(",\n");
		builder.append("name: ").append(getName()).append(",\n");
		builder.append("path: ").append(getPath()).append(",\n");
		builder.append("unit: ").append(getUnit().toString()).append(",\n");
		builder.append("dataSourceName: ").append(getDataSourceName()).append(",\n");
		builder.append("dataSourceXid: ").append(getDataSourceXid()).append(",\n");
		builder.append("tags: ").append(getTags()).append(",\n");

		if(this.wrapper != null)
			builder.append("runtime: ").append(this.wrapper.getHelp());
		else
			builder.append("runtime: null (not enabled),\n");
		this.helpImpl(builder);

		builder.append(" }\n");
		return builder.toString();
	}
	
}
