/*
 * Copyright (C) 2021 Radix IoT LLC. All rights reserved.
 */

package com.serotonin.m2m2.db.upgrade;

import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.util.Optional;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.infiniteautomation.mango.db.tables.SystemSettings;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.DatabaseProxy;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.module.DatabaseSchemaDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;

public class DatabaseSchemaUpgrader {

    private final Logger LOG = LoggerFactory.getLogger(DatabaseSchemaUpgrader.class);
    private final DatabaseProxy databaseProxy;
    private final DSLContext context;
    private final ClassLoader classLoader;
    private final SystemSettings systemSettings = SystemSettings.SYSTEM_SETTINGS;

    public DatabaseSchemaUpgrader(DatabaseProxy databaseProxy, DSLContext context, ClassLoader classLoader) {
        this.databaseProxy = databaseProxy;
        this.context = context;
        this.classLoader = classLoader;
    }

    public Optional<String> getSystemSetting(String key) {
        return context.select(systemSettings.settingValue)
                .from(systemSettings)
                .where(systemSettings.settingName.eq(key))
                .fetchOptional(systemSettings.settingValue);
    }

    public void setSystemSetting(String key, String value) {
        context.update(systemSettings)
                .set(systemSettings.settingValue, value)
                .where(systemSettings.settingName.eq(key))
                .execute();
    }

    public void checkCoreUpgrade() {
        checkUpgrade(SystemSettingsDao.DATABASE_SCHEMA_VERSION, Common.getDatabaseSchemaVersion(), DBUpgrade.class
                .getPackage().getName(), ModuleRegistry.CORE_MODULE_NAME);
        LOG.info("Starting instance with core version " + Common.getVersion() + ", schema v"
                + Common.getDatabaseSchemaVersion());
    }

    public void checkModuleUpgrade(DatabaseSchemaDefinition def) {
        String name = def.getModule().getName();
        checkUpgrade(SystemSettingsDao.DATABASE_SCHEMA_VERSION + "." + name, def.getDatabaseSchemaVersion(),
                def.getUpgradePackage(), name);
    }

    private void checkUpgrade(String settingsKey, int codeVersion, String pkg, String moduleName) {
        // If this is a very old version of the system, there may be multiple upgrades to run, so start a loop.
        while (true) {
            // Get the current schema version.
            int schemaVersion = getSystemSetting(settingsKey)
                    .map(Integer::parseInt)
                    .orElse(-1);

            if (schemaVersion <= 0) {
                if (ModuleRegistry.CORE_MODULE_NAME.equals(moduleName))
                    // Probably an old core. Assume the version to be 1 to do complete upgrade
                    schemaVersion = 1;
                else {
                    // Probably a new module. Put the current code version into the database.
                    setSystemSetting(settingsKey, Integer.toString(codeVersion));
                    schemaVersion = codeVersion;
                }
            }

            // Convert the schema version to the class name convention. This simply means replacing dots with
            // underscores and prefixing 'Upgrade' and this package.
            String upgradeClassname = pkg + ".Upgrade" + schemaVersion;

            // See if there is a class with this name.
            Class<?> clazz = null;
            DBUpgrade upgrade = null;
            try {
                clazz = Class.forName(upgradeClassname, true, classLoader);
            } catch (ClassNotFoundException e) {
                // no op
            }

            if (clazz != null) {
                try {
                    Constructor<?> constructor = clazz.getConstructor();
                    upgrade = (DBUpgrade) constructor.newInstance();
                } catch (Exception e) {
                    // Should never happen so wrap in a runtime and rethrow.
                    throw new ShouldNeverHappenException(e);
                }
            }

            if (upgrade == null) {
                if (schemaVersion != codeVersion)
                    LOG.warn("The code version " + codeVersion + " of module " + moduleName
                            + " does not match the schema version " + schemaVersion);
                break;
            }

            try {
                LOG.warn("Upgrading '" + moduleName + "' from " + schemaVersion + " to "
                        + upgrade.getNewSchemaVersion());
                upgrade.initialize(databaseProxy);
                upgrade.upgrade();
                setSystemSetting(settingsKey, upgrade.getNewSchemaVersion());
            } catch (Exception e) {
                try (PrintWriter writer = new PrintWriter(upgrade.createUpdateLogOutputStream())) {
                    e.printStackTrace(writer);
                }
                throw new ShouldNeverHappenException(e);
            }
        }
    }

}
