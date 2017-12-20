/**
 * @copyright 2017 {@link http://infiniteautomation.com|Infinite Automation Systems, Inc.} All rights reserved.
 * @author Terry Packer
 */
package com.serotonin.m2m2.db.upgrade;

import java.util.HashMap;
import java.util.Map;

import com.serotonin.m2m2.db.DatabaseProxy;

/**
 * Add performance indexes to the Events table
 *
 * @author Terry Packer
 */
public class Upgrade22 extends DBUpgrade{

    @Override
    protected void upgrade() throws Exception {
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), mysql);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), h2);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), mssql);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), postgres);
        runScript(scripts);
    }
    
    @Override
    protected String getNewSchemaVersion() {
        return "23";
    }
    
    private final String[] mysql = new String[] {
            "ALTER TABLE events ADD INDEX events_performance2 (`rtnApplicable` ASC, `rtnTs` ASC);",
            "ALTER TABLE events ADD INDEX events_performance3 (`typeName` ASC, `subTypeName` ASC, `typeRef1` ASC);",
            "ALTER TABLE userComments ADD INDEX userComments_performance1 (`commentType` ASC, `typeKey` ASC);"
    };
    
    private final String[] h2 = new String[] {
            "CREATE INDEX events_performance2 ON events (`rtnApplicable` ASC, `rtnTs` ASC);",
            "CREATE INDEX events_performance3 ON events (`typeName` ASC, `subTypeName` ASC, `typeRef1` ASC);",
            "CREATE INDEX userComments_performance1 ON userComments (`commentType` ASC, `typeKey` ASC);", 
    };
    
    private final String[] mssql = new String[] {
            "CREATE INDEX events_performance2 ON events (rtnApplicable ASC, rtnTs ASC);",
            "CREATE INDEX events_performance3 ON events (typeName ASC, subTypeName ASC, typeRef1 ASC);",
            "CREATE INDEX userComments_performance1 ON userComments (commentType ASC, typeKey ASC);"
    };
    
    private final String[] postgres = new String[] {
            "ALTER TABLE events ADD INDEX events_performance2 (`rtnApplicable` ASC, `rtnTs` ASC);",
            "ALTER TABLE events ADD INDEX events_performance3 (`typeName` ASC, `subTypeName` ASC, `typeRef1` ASC);",
            "ALTER TABLE userComments ADD INDEX userComments_performance1 (`commentType` ASC, `typeKey` ASC);"
    };
}
