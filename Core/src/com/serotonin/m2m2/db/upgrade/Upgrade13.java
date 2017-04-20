/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 * @author Jared Wiltshire
 */
package com.serotonin.m2m2.db.upgrade;

import java.util.HashMap;
import java.util.Map;

import com.serotonin.m2m2.db.DatabaseProxy;

public class Upgrade13 extends DBUpgrade {

    @Override
    public void upgrade() throws Exception {
        // Run the script.
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.DERBY.name(), derbyScript);
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), mysqlScript);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), mssqlScript);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), h2Script);
        runScript(scripts);
    }

    @Override
    protected String getNewSchemaVersion() {
        return "14";
    }

    private final String[] mssqlScript = {
        "ALTER TABLE users ADD COLUMN name nvarchar(255) NOT NULL DEFAULT '';",
        "UPDATE users SET name='';",
        "ALTER TABLE users ADD COLUMN locale nvarchar(50) NOT NULL DEFAULT '';",
        "UPDATE users SET locale='';"
    };
    private final String[] derbyScript = {
        "ALTER TABLE users ADD COLUMN name varchar(255) NOT NULL DEFAULT '';",
        "UPDATE users SET name='';",
        "ALTER TABLE users ADD COLUMN locale varchar(50) NOT NULL DEFAULT '';",
        "UPDATE users SET locale='';"
    };
    private final String[] mysqlScript = {
        "ALTER TABLE users ADD COLUMN name nvarchar(255) NOT NULL DEFAULT '';",
        "UPDATE users SET name='';",
        "ALTER TABLE users ADD COLUMN locale nvarchar(50) NOT NULL DEFAULT '';",
        "UPDATE users SET locale='';"
    };
    private final String[] h2Script = {
        "ALTER TABLE users ADD COLUMN name varchar(255) NOT NULL DEFAULT '';",
        "UPDATE users SET name='';",
        "ALTER TABLE users ADD COLUMN locale varchar(50) NOT NULL DEFAULT '';",
        "UPDATE users SET locale='';"
    };
}
