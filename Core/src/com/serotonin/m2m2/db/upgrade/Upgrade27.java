/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.db.upgrade;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.springframework.jdbc.core.RowCallbackHandler;

import com.serotonin.m2m2.db.DatabaseProxy;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.util.SerializationHelper;

/**
 * Add settable column to data points table
 * 
 * @author Terry Packer
 *
 */
public class Upgrade27 extends DBUpgrade {

    @Override
    protected void upgrade() throws Exception {
        
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), sql);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), sql);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), sql);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), sql);
        runScript(scripts);
        
        //Now update all the data point's to have a value
        ejt.query("SELECT data,id FROM dataPoints", new RowCallbackHandler() {

            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int i = 0;
                DataPointVO dp = (DataPointVO) SerializationHelper.readObjectInContext(rs.getBinaryStream(++i));
                int id = rs.getInt(++i);
                ejt.update("UPDATE dataPoints SET settable=? WHERE id=?", new Object[] {boolToChar(dp.getPointLocator().isSettable()), id});
            }
            
        });
    }
    
    private String[] sql = new String[]{
            "ALTER TABLE dataPoints ADD COLUMN settable char(1);",
    };

    @Override
    protected String getNewSchemaVersion() {
        return "28";
    }
}
