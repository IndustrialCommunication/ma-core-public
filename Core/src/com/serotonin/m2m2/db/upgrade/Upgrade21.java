/**
 * Copyright (C) 2017 Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.db.upgrade;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;

import com.serotonin.m2m2.db.DatabaseProxy;

/**
 * @author Jared Wiltshire
 */
public class Upgrade21 extends DBUpgrade {

    private static final Log LOG = LogFactory.getLog(Upgrade21.class);
    
    @Override
    protected void upgrade() throws Exception {
        OutputStream out = createUpdateLogOutputStream();
        //Update User table to make Unique usernames only
        //First remove duplicate users
        try {
            Map<Integer, String> toRemove = query("SELECT id,username FROM users ORDER BY id asc", new ResultSetExtractor<Map<Integer,String>>(){
    
                @Override
                public Map<Integer, String> extractData(ResultSet rs)
                        throws SQLException, DataAccessException {
                    Map<Integer, String> remove = new HashMap<>();
                    Map<String, Integer> existing = new HashMap<>();
                    while(rs.next()) {
                        if(null != existing.put(rs.getString(2), rs.getInt(1))) {
                            remove.put(rs.getInt(1), rs.getString(2));
                        }
                    }
                    return remove;
                }
                
            });
            
    
            StringBuilder deleteSQL = new StringBuilder("DELETE FROM users WHERE id IN (");
            MutableInt count = new MutableInt(toRemove.keySet().size());
            toRemove.keySet().stream().forEach((key) -> {
                LOG.warn("Removing duplicate user '" + toRemove.get(key) + "' with id " + key);
                PrintWriter pw = new PrintWriter(out);
                pw.write("WARN: Removing duplicate user '" + toRemove.get(key) + "' with id " + key + "\n");
                pw.flush();
                count.decrement();
                deleteSQL.append(key);
                if(count.getValue() > 0)
                    deleteSQL.append(",");
            });
            deleteSQL.append(");");
            Map<String, String[]> scripts = new HashMap<>();
            scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), new String[] {deleteSQL.toString()});
            scripts.put(DatabaseProxy.DatabaseType.H2.name(), new String[] {deleteSQL.toString()});
            scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), new String[] {deleteSQL.toString()});
            scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), new String[] {deleteSQL.toString()});
            runScript(scripts, out);
            
    
            //Finally update the users table with new columns
            scripts.clear();
            scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), mysql);
            scripts.put(DatabaseProxy.DatabaseType.H2.name(), h2);
            scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), mssql);
            scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), postgres);
            runScript(scripts, out);
        } finally {
            out.flush();
            out.close();
        }

    }

    @Override
    protected String getNewSchemaVersion() {
        return "22";
    }
    
    private final String[] mysql = new String[] {
            "ALTER TABLE users ADD COLUMN tokenVersion INT;",
            "ALTER TABLE users ADD COLUMN passwordVersion INT;",
            "UPDATE users SET tokenVersion = 1;",
            "UPDATE users SET passwordVersion = 1;",
            "ALTER TABLE users MODIFY COLUMN tokenVersion INT NOT NULL;",
            "ALTER TABLE users MODIFY COLUMN passwordVersion INT NOT NULL;",
            "ALTER TABLE users ADD CONSTRAINT username_unique UNIQUE(username);"
    };
    
    private final String[] h2 = new String[] {
            "ALTER TABLE users ADD COLUMN tokenVersion INT;",
            "ALTER TABLE users ADD COLUMN passwordVersion INT;",
            "UPDATE users SET tokenVersion = 1;",
            "UPDATE users SET passwordVersion = 1;",
            "ALTER TABLE users ALTER COLUMN tokenVersion INT NOT NULL;",
            "ALTER TABLE users ALTER COLUMN passwordVersion INT NOT NULL;",
            "ALTER TABLE users ADD CONSTRAINT username_unique UNIQUE(username);"
    };
    
    private final String[] mssql = new String[] {
            "ALTER TABLE users ADD COLUMN tokenVersion INT;",
            "ALTER TABLE users ADD COLUMN passwordVersion INT;",
            "UPDATE users SET tokenVersion = 1;",
            "UPDATE users SET passwordVersion = 1;",
            "ALTER TABLE users ALTER COLUMN tokenVersion INT NOT NULL;",
            "ALTER TABLE users ALTER COLUMN passwordVersion INT NOT NULL;",
            "alter table users add constraint username_unique unique (username);"
    };
    
    private final String[] postgres = new String[] {
            "ALTER TABLE users ADD COLUMN tokenVersion INT;",
            "ALTER TABLE users ADD COLUMN passwordVersion INT;",
            "UPDATE users SET tokenVersion = 1;",
            "UPDATE users SET passwordVersion = 1;",
            "ALTER TABLE users ALTER COLUMN tokenVersion INT NOT NULL;",
            "ALTER TABLE users ALTER COLUMN passwordVersion INT NOT NULL;",
            "ALTER TABLE users ADD CONSTRAINT username_unique UNIQUE (username);"
    };
}
