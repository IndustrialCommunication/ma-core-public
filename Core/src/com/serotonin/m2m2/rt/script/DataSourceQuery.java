/**
 * Copyright (C) 2015 Infinite Automation Software. All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.rt.script;

import java.util.ArrayList;
import java.util.List;

import javax.script.Bindings;
import javax.script.ScriptEngine;

import org.springframework.beans.factory.annotation.Autowired;

import com.infiniteautomation.mango.spring.service.MangoJavaScriptService;
import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.script.ScriptUtility;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.db.dao.DataSourceDao;
import com.serotonin.m2m2.rt.dataImage.DataPointRT;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;

import net.jazdw.rql.parser.ASTNode;
import net.jazdw.rql.parser.RQLParser;

/**
 * 
 * Scripting Utility to query data points
 * 
 * @author Terry Packer
 *
 */
public class DataSourceQuery extends ScriptUtility {
	
	public static final String CONTEXT_KEY = "DataSourceQuery";

	private ScriptEngine engine;
	private ScriptPointValueSetter setter;
	private RQLParser parser = new RQLParser();
	
    @Autowired
    public DataSourceQuery(MangoJavaScriptService service, PermissionService permissionService) {
        super(service, permissionService);
    }

    @Override
    public String getContextKey() {
        return CONTEXT_KEY;
    }
	
    @Override
    public void takeContext(ScriptEngine engine, Bindings engineScope, 
            ScriptPointValueSetter setter, List<JsonImportExclusion> importExclusions, boolean testRun) {
        this.engine = engine;
        this.setter = setter;
    }
	
	public List<DataSourceWrapper> query(String query){
		ASTNode root = parser.parse(query);
        List<DataSourceWrapper> results = new ArrayList<DataSourceWrapper>();		
		DataSourceDao.getInstance().rqlQuery(root, (ds, index) -> {
	          if(permissionService.hasDataSourcePermission(permissions, ds)){
	                List<DataPointWrapper> points = getPointsForSource(ds);
	                results.add(new DataSourceWrapper(ds, points));
	            }
		});
		return results;
	}
	
	public DataSourceWrapper byXid(String xid) {
	    DataSourceVO<?> ds = DataSourceDao.getInstance().getByXid(xid);
	    if(ds == null)
	        return null;
	    
	    if(permissionService.hasDataSourcePermission(permissions, ds)) {
	        List<DataPointWrapper> points = getPointsForSource(ds);
            return new DataSourceWrapper(ds, points);
	    } else
	        return null;
	}
	
    /**
     * Helper to extract points for a source
	 * @param ds
	 * @return
	 */
	private List<DataPointWrapper> getPointsForSource(DataSourceVO<?> ds) {
		List<DataPointWrapper> points = new ArrayList<DataPointWrapper>();
		
		List<DataPointVO> dataPoints = DataPointDao.getInstance().getDataPoints(ds.getId());
		
		for(DataPointVO vo : dataPoints){
			DataPointRT rt = Common.runtimeManager.getDataPoint(vo.getId());
			AbstractPointWrapper wrapper = null;
			if(rt != null)
				wrapper = service.wrapPoint(engine, rt, setter);
			points.add(new DataPointWrapper(vo, wrapper));	
		}
		return points;
	}

	public String getHelp(){
    	return toString();
    }
    
	public String toString(){
		StringBuilder builder = new StringBuilder();
		builder.append("{ ");
		builder.append("query(rql): List<DataSourceWrapper>()");
		builder.append(" }\n");
		return builder.toString();
	}
	
}
