/**
 * Copyright (C) 2019  Infinite Automation Software. All rights reserved.
 */
package com.serotonin.m2m2.db.upgrade;

import java.io.OutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.infiniteautomation.mango.spring.service.PermissionService;
import com.infiniteautomation.mango.util.Functions;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.DatabaseProxy;
import com.serotonin.m2m2.db.dao.RoleDao;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.module.PermissionDefinition;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.FileStore;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.json.JsonDataVO;
import com.serotonin.m2m2.vo.mailingList.MailingList;
import com.serotonin.m2m2.vo.role.RoleVO;

/**
 * Add roles and roleMappings tables
 * 
 * MailingList - remove readPermissions and editPermissions
 *
 *
 * @author Terry Packer
 *
 */
public class Upgrade29 extends DBUpgrade {

    private final Log LOG = LogFactory.getLog(Upgrade29.class);
    
    @Override
    protected void upgrade() throws Exception {
        OutputStream out = createUpdateLogOutputStream();

        try {
            Map<String, RoleVO> roles = new HashMap<>();
            createRolesTables(roles, out);
            convertUsers(roles, out);
            convertSystemSettingsPermissions(roles, out);
            convertDataPoints(roles, out);
            convertDataSources(roles, out);
            convertJsonData(roles, out);
            convertMailingLists(roles, out);
            convertFileStores(roles, out);
        } catch(Exception e){
            LOG.error("Upgrade 29 failed.", e);
        } finally {
            out.flush();
            out.close();
        }
    }

    private void createRolesTables(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), createRolesMySQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), createRolesSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), createRolesMSSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), createRolesSQL);
        runScript(scripts, out);
        
        //Add default user and superadmin roles
        RoleVO superadminRole = new RoleVO(RoleDao.SUPERADMIN_ROLE_NAME, Common.translate("roles.superadmin"));
        superadminRole.setId(ejt.doInsert("INSERT INTO roles (xid,name) VALUES (?,?)", new Object[] {superadminRole.getXid(), superadminRole.getName()}));
        roles.put(superadminRole.getXid(), superadminRole);
        RoleVO userRole = new RoleVO(RoleDao.USER_ROLE_NAME, Common.translate("roles.user"));
        userRole.setId(ejt.doInsert("INSERT INTO roles (xid,name) VALUES (?,?)", new Object[] {userRole.getXid(), userRole.getName()}));
        roles.put(userRole.getXid(), userRole);
    }
    
    private void convertUsers(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Move current permissions to roles
        ejt.query("SELECT id, permissions FROM users", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int userId = rs.getInt(1);
                //Get user's current permissions
                Set<String> permissions = explodePermissionGroups(rs.getString(2));
                Set<String> userRoles = new HashSet<>();
                for(String permission : permissions) {
                    //ensure all roles are lower case and don't have spaces on the ends
                    permission = permission.trim();
                    String role = permission.toLowerCase();
                    roles.compute(role, (k,r) -> {
                        if(r == null) {
                            r = new RoleVO(role, role);
                            r.setId(ejt.doInsert("INSERT INTO roles (xid, name) values (?,?)", new Object[] {role, role}));
                            
                        }
                        if(!userRoles.contains(role)) {
                            //Add a mapping
                            ejt.doInsert("INSERT INTO userRoleMappings (roleId, userId) VALUES (?,?,)", 
                                    new Object[] {
                                            r.getId(),
                                            userId
                                    });
                            userRoles.add(role);
                        }
                        return r;
                    });
                }
            }
        });
        
        //Drop the permissions column
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), userSQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), userSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), userSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), userSQL);
        runScript(scripts, out);
    }
    
    private void convertSystemSettingsPermissions(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Check all permissions
        for (PermissionDefinition def : ModuleRegistry.getDefinitions(PermissionDefinition.class)) {
            //Move to roles and map them
            ejt.query("SELECT settingValue FROM systemSettings WHERE settingName=?", new Object[] {def.getPermissionTypeName()}, new RowCallbackHandler() {
                @Override
                public void processRow(ResultSet rs) throws SQLException {
                    //Add role/mapping
                    insertMapping(null, def.getPermissionTypeName(), null, explodePermissionGroups(rs.getString(1)), roles);
                }
            });
            //Delete the setting
            ejt.update("DELETE FROM systemSettings WHERE settingName=?", new Object[] {def.getPermissionTypeName()});
        }
    }
    
    private void convertDataPoints(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Move current permissions to roles
        ejt.query("SELECT id, readPermission, setPermission FROM dataPoints", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int voId = rs.getInt(1);
                //Add role/mapping
                Set<String> readPermissions = explodePermissionGroups(rs.getString(2));
                insertMapping(voId, DataPointVO.class.getSimpleName(), PermissionService.READ, readPermissions, roles);
                Set<String> setPermissions = explodePermissionGroups(rs.getString(3));
                insertMapping(voId, DataPointVO.class.getSimpleName(), PermissionService.SET, setPermissions, roles);
            }
        });
        
        
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), dataPointsSQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), dataPointsSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), dataPointsSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), dataPointsSQL);
        runScript(scripts, out);
    }
    
    private void convertDataSources(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Move current permissions to roles
        ejt.query("SELECT id, editPermission FROM dataSources", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int voId = rs.getInt(1);
                //Add role/mapping
                Set<String> editPermissions = explodePermissionGroups(rs.getString(2));
                insertMapping(voId, DataSourceVO.class.getSimpleName(), PermissionService.EDIT, editPermissions, roles);
            }
        });
        
        
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), dataSourcesSQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), dataSourcesSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), dataSourcesSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), dataSourcesSQL);
        runScript(scripts, out);
    }
    
    private void convertJsonData(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Move current permissions to roles
        ejt.query("SELECT id, readPermission, editPermission FROM jsonData", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int voId = rs.getInt(1);
                //Add role/mapping
                Set<String> readPermissions = explodePermissionGroups(rs.getString(2));
                insertMapping(voId, JsonDataVO.class.getSimpleName(), PermissionService.READ, readPermissions, roles);
                Set<String> editPermissions = explodePermissionGroups(rs.getString(3));
                insertMapping(voId, JsonDataVO.class.getSimpleName(), PermissionService.EDIT, editPermissions, roles);
            }
        });
        
        
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), jsonDataSQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), jsonDataSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), jsonDataSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), jsonDataSQL);
        runScript(scripts, out);
    }
    
    private void convertMailingLists(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Move current permissions to roles
        ejt.query("SELECT id, readPermission, editPermission FROM mailingLists", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int voId = rs.getInt(1);
                //Add role/mapping
                Set<String> readPermissions = explodePermissionGroups(rs.getString(2));
                insertMapping(voId, MailingList.class.getSimpleName(), PermissionService.READ, readPermissions, roles);
                Set<String> editPermissions = explodePermissionGroups(rs.getString(3));
                insertMapping(voId, MailingList.class.getSimpleName(), PermissionService.EDIT, editPermissions, roles);
            }
        });
        
        
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), mailingListSQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), mailingListSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), mailingListSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), mailingListSQL);
        runScript(scripts, out);
    }
    
    private void convertFileStores(Map<String, RoleVO> roles, OutputStream out) throws Exception {
        //Move current permissions to roles
        ejt.query("SELECT id, readPermission, writePermission FROM fileStores", new RowCallbackHandler() {
            @Override
            public void processRow(ResultSet rs) throws SQLException {
                int voId = rs.getInt(1);
                //Add role/mapping
                Set<String> readPermissions = explodePermissionGroups(rs.getString(2));
                insertMapping(voId, FileStore.class.getSimpleName(), PermissionService.READ, readPermissions, roles);
                Set<String> writePermissions = explodePermissionGroups(rs.getString(3));
                insertMapping(voId, FileStore.class.getSimpleName(), PermissionService.WRITE, writePermissions, roles);
            }
        });
        
        
        Map<String, String[]> scripts = new HashMap<>();
        scripts.put(DatabaseProxy.DatabaseType.MYSQL.name(), fileStoreSQL);
        scripts.put(DatabaseProxy.DatabaseType.H2.name(), fileStoreSQL);
        scripts.put(DatabaseProxy.DatabaseType.MSSQL.name(), fileStoreSQL);
        scripts.put(DatabaseProxy.DatabaseType.POSTGRES.name(), fileStoreSQL);
        runScript(scripts, out);
    }
    
    //Roles
    private String[] createRolesSQL = new String[] {
            "CREATE TABLE roles (id int not null auto_increment, xid varchar(100) not null, name varchar(255) not null, primary key (id));", 
            "ALTER TABLE roles ADD CONSTRAINT rolesUn1 UNIQUE (xid);",
            
            "CREATE TABLE roleMappings (roleId int not null, voId int, voType varchar(255), permissionType varchar(255) not null);",
            "ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;" + 
            "ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsUn1 UNIQUE (roleId,voId,voType,permissionType);",
            
            "CREATE TABLE userRoleMappings (roleId int not null, userId int not null);",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);"

    };
    private String[] createRolesMySQL = new String[] {
            "CREATE TABLE roles (id int not null auto_increment, xid varchar(100) not null, name varchar(255) not null, primary key (id)) engine=InnoDB;", 
            "ALTER TABLE roles ADD CONSTRAINT rolesUn1 UNIQUE (xid);",
            
            "CREATE TABLE roleMappings (roleId int not null, voId int, voType varchar(255), permissionType varchar(255) not null) engine=InnoDB;",
            "ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;" + 
            "ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsUn1 UNIQUE (roleId,voId,voType,permissionType);",
            
            "CREATE TABLE userRoleMappings (roleId int not null, userId int not null);",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);"
    };
    private String[] createRolesMSSQL = new String[] {
            "CREATE TABLE roles (id int not null auto_increment, xid varchar(100) not null, name varchar(255) not null, primary key (id));", 
            "ALTER TABLE roles ADD CONSTRAINT rolesUn1 UNIQUE (xid);",
            
            "CREATE TABLE roleMappings (roleId int not null, voId int, voType nvarchar(255), permissionType varchar(255) not null);",
            "ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;" + 
            "ALTER TABLE roleMappings ADD CONSTRAINT roleMappingsUn1 UNIQUE (roleId,voId,voType,permissionType);",
            
            "CREATE TABLE userRoleMappings (roleId int not null, userId int not null);",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk1 FOREIGN KEY (roleId) REFERENCES roles(id) ON DELETE CASCADE;",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsFk2 FOREIGN KEY (userId) REFERENCES users(id) ON DELETE CASCADE;",
            "ALTER TABLE userRoleMappings ADD CONSTRAINT userRoleMappingsUn1 UNIQUE (roleId,userId);"
    };
    
    //Users
    private String[] userSQL = new String[] {
            "ALTER TABLE users DROP COLUMN permissions;",
    };
    
    private String[] dataPointsSQL = new String[] {
            "ALTER TABLE dataPoints DROP COLUMN readPermission;",
            "ALTER TABLE dataPoints DROP COLUMN setPermission;",
            "ALTER TABLE dataPoints DROP COLUMN pointFolderId;"
    };
    
    private String[] dataSourcesSQL = new String[] {
            "ALTER TABLE dataSources DROP COLUMN editPermission;",
    };
    
    //Mailing lists
    private String[] mailingListSQL = new String[] {
            "ALTER TABLE mailingLists DROP COLUMN readPermission;",
            "ALTER TABLE mailingLists DROP COLUMN editPermission;"
    };
    
    private String[] jsonDataSQL = new String[] {
            "ALTER TABLE jsonData DROP COLUMN readPermission;",
            "ALTER TABLE jsonData DROP COLUMN editPermission;"
    };

    private String[] fileStoreSQL = new String[] {
            "ALTER TABLE fileStores DROP COLUMN readPermission;",
            "ALTER TABLE fileStores DROP COLUMN writePermission;"
    };

    /**
     * Get all existing roles so we can ensure we don't create duplicate roles only new mappings
     * @return
     */
    protected Map<String, RoleVO> getExistingRoles() {
        return ejt.query("SELECT xid,name FROM roles", new ResultSetExtractor<Map<String, RoleVO>>() {

            @Override
            public Map<String, RoleVO> extractData(ResultSet rs) throws SQLException, DataAccessException {
                Map<String, RoleVO> mappings = new HashMap<>();
                while(rs.next()) {
                    String xid = rs.getString(1);
                    String name = rs.getString(2);
                    mappings.put(xid, new RoleVO(xid, name));
                }
                return mappings;
            }
        });
    }
    
    /**
     * Ensure role exists and insert mappings for this permission
     *  this is protected for use in modules for this upgrade
     * @param voId
     * @param voType
     * @param permissionType
     * @param existingPermission
     * @param roles
     */
    protected void insertMapping(Integer voId, String voType, String permissionType, Set<String> existingPermissions, Map<String, RoleVO> roles) {
        //Ensure each role is only used 1x for this permission
        Set<String> voRoles = new HashSet<>();
        for(String permission : existingPermissions) {
            //ensure all roles are lower case and don't have spaces on the ends
            permission = permission.trim();
            String role = permission.toLowerCase();
            roles.compute(role, (k,r) -> {
                if(r == null) {
                    r = new RoleVO(role, role);
                    r.setId(ejt.doInsert("INSERT INTO roles (xid, name) values (?,?)", new Object[] {role, role}));
                }
                if(!voRoles.contains(role)) {
                    //Add a mapping
                    ejt.doInsert("INSERT INTO roleMappings (roleId, voId, voType, permissionType) VALUES (?,?,?,?)", 
                            new Object[] {
                                    r.getId(),
                                    voId,
                                    voType,
                                    permissionType
                            });
                    voRoles.add(role);
                }
                return r;
            });
        }
    }
    
    
    /**
     * For use by modules in this upgrade
     * @param groups
     * @return
     */
    protected Set<String> explodePermissionGroups(String groups) {
        if (groups == null || groups.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> set = new HashSet<>();
        for (String s : groups.split(",")) {
            s = s.replaceAll(Functions.WHITESPACE_REGEX, "");
            if (!s.isEmpty()) {
                set.add(s);
            }
        }
        return Collections.unmodifiableSet(set);
    }
    
    @Override
    protected String getNewSchemaVersion() {
        return "30";
    }

}
