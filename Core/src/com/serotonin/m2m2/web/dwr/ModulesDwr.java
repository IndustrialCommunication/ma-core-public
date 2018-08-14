/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.web.dwr;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;

import com.github.zafarkhaja.semver.Version;
import com.serotonin.ShouldNeverHappenException;
import com.serotonin.db.pair.StringStringPair;
import com.serotonin.json.JsonException;
import com.serotonin.json.JsonWriter;
import com.serotonin.json.type.JsonArray;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonString;
import com.serotonin.json.type.JsonTypeReader;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.Constants;
import com.serotonin.m2m2.ICoreLicense;
import com.serotonin.m2m2.IMangoLifecycle;
import com.serotonin.m2m2.UpgradeVersionState;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.module.Module;
import com.serotonin.m2m2.module.ModuleNotificationListener;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.rt.maint.work.BackupWorkItem;
import com.serotonin.m2m2.rt.maint.work.DatabaseBackupWorkItem;
import com.serotonin.m2m2.shared.ModuleUtils;
import com.serotonin.m2m2.util.timeout.HighPriorityTask;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.web.dwr.util.DwrPermission;
import com.serotonin.provider.Providers;
import com.serotonin.web.http.HttpUtils4;

public class ModulesDwr extends BaseDwr implements ModuleNotificationListener {
    static final Log LOG = LogFactory.getLog(ModulesDwr.class);
    
    private static final List<ModuleNotificationListener> listeners = new CopyOnWriteArrayList<ModuleNotificationListener>();
    
    private static Thread SHUTDOWN_TASK;
    private static final Object SHUTDOWN_TASK_LOCK = new Object();
    
    private static UpgradeDownloader UPGRADE_DOWNLOADER;
    private static final Object UPGRADE_DOWNLOADER_LOCK = new Object();
    
    /**
     * 
     */
    public ModulesDwr() {
        //We are a listener
        listeners.add(this);
    }
    
    @DwrPermission(admin = true)
    public boolean toggleDeletion(String moduleName) {
        Module module = ModuleRegistry.getModule(moduleName);
        module.setMarkedForDeletion(!module.isMarkedForDeletion());
        return module.isMarkedForDeletion();
    }

    @DwrPermission(admin = true)
    public static ProcessResult scheduleRestart() {
        ProcessResult result = new ProcessResult();
        synchronized(SHUTDOWN_TASK_LOCK){
	        if (SHUTDOWN_TASK == null) {
	            long timeout = Common.getMillis(Common.TimePeriods.SECONDS, 10);
	            IMangoLifecycle lifecycle = Providers.get(IMangoLifecycle.class);
	            SHUTDOWN_TASK = lifecycle.scheduleShutdown(timeout, true, Common.getHttpUser());
	            //Get the redirect page
	            result.addData("shutdownUri", "/shutdown.htm");
	        }
	        else {
	            result.addData("message", Common.translate("modules.restartAlreadyScheduled"));
	        }
        }

        return result;
    }

    @DwrPermission(admin = true)
    public static ProcessResult scheduleShutdown() {
        ProcessResult result = new ProcessResult();
        synchronized(SHUTDOWN_TASK_LOCK){
	        if (SHUTDOWN_TASK == null) {
	            long timeout = Common.getMillis(Common.TimePeriods.SECONDS, 10);
	
	            //Ensure our lifecycle state is set to PRE_SHUTDOWN
	            IMangoLifecycle lifecycle = Providers.get(IMangoLifecycle.class);
	            SHUTDOWN_TASK = lifecycle.scheduleShutdown(timeout, false, Common.getHttpUser());
	            //Get the redirect page
	            result.addData("shutdownUri", "/shutdown.htm");
	        }
	        else {
	            result.addData("message", Common.translate("modules.shutdownAlreadyScheduled"));
	        }
        }

        return result;
    }

    @DwrPermission(admin = true)
    public ProcessResult versionCheck() {
        ProcessResult result = new ProcessResult();

        if (UPGRADE_DOWNLOADER != null) {
            result.addData("error", Common.translate("modules.versionCheck.occupied"));
            return result;
        }

        try {
            JsonValue jsonResponse = getAvailableUpgrades();

            if (jsonResponse == null) {
                result.addData("error", new TranslatableMessage("modules.versionCheck.storeNotSet").translate(Common.getHttpUser().getTranslations()));
                return result;
            }
            if (jsonResponse instanceof JsonString)
                result.addData("error", jsonResponse.toString());
            else {
                JsonObject root = jsonResponse.toJsonObject();
                result.addData("upgrades", root.get("upgrades").toNative());
                result.addData("newInstalls", root.get("newInstalls").toNative());
                if (root.containsKey("upgradesError"))
                    result.addData("upgradesError", root.getString("upgradesError"));
                if (root.containsKey("updates")) {
                    result.addData("updates", root.get("updates").toNative());
                    result.addData("newInstalls-oldCore",
                            root.get("newInstalls-oldCore").toNative());
                }
                if (root.containsKey("missingModules"))
                    result.addData("missingModules",
                            root.getJsonArray("missingModules").toNative());
            }
        } catch (UnknownHostException e) {
            LOG.error("", e);
            result.addData("unknownHost", e.getMessage());
        } catch (Exception e) {
            LOG.error("", e);
            result.addData("error", e.getMessage());
        }
        return result;
    }

    
    @DwrPermission(admin = true)
    public static String startDownloads(List<StringStringPair> modules, boolean backup, boolean restart) {
        synchronized(UPGRADE_DOWNLOADER_LOCK){
            if (UPGRADE_DOWNLOADER != null)
                return Common.translate("modules.versionCheck.occupied");
        }

        // Check if the selected modules will result in a version-consistent system.
        try {
            // Create the request
            Map<String, Object> json = new HashMap<>();
            Map<String, String> jsonModules = new HashMap<>();
            json.put("modules", jsonModules);

            Version coreVersion = Common.getVersion();
            
            jsonModules.put("core", coreVersion.toString());
            for (StringStringPair module : modules)
                jsonModules.put(module.getKey(), module.getValue());

            StringWriter stringWriter = new StringWriter();
            new JsonWriter(Common.JSON_CONTEXT, stringWriter).writeObject(json);
            String requestData = stringWriter.toString();

            // Send the request
            String baseUrl = Common.envProps.getString("store.url");
            if(StringUtils.isEmpty(baseUrl)) {
                LOG.info("No consistency check performed as no store.url is defined in env.properties.");
                return "No consistency check performed as no store.url is defined in env.properties.";
            }
            baseUrl += "/servlet/consistencyCheck";

            HttpPost post = new HttpPost(baseUrl);
            post.setEntity(new StringEntity(requestData));

            String responseData = HttpUtils4.getTextContent(Common.getHttpClient(), post, 1);

            // Parse the response
            JsonTypeReader jsonReader = new JsonTypeReader(responseData);
            String result = jsonReader.read().toString();
            if (!"ok".equals(result))
                return result;
        }
        catch (Exception e) {
            LOG.error("", e);
            return e.getMessage();
        }

        synchronized(UPGRADE_DOWNLOADER_LOCK){
	        // Ensure that 2 downloads cannot start at the same time.
	        if (UPGRADE_DOWNLOADER == null) {
                    UPGRADE_DOWNLOADER = new UpgradeDownloader(modules, backup, restart, Common.getHttpUser());
                    //Clear out common info
                    resetUpgradeStatus();
                    Common.backgroundProcessing.execute(UPGRADE_DOWNLOADER);
	        } else
                    return Common.translate("modules.versionCheck.occupied");
        }

        return null;
    }
    
    @DwrPermission(admin = true)
    public ProcessResult tryCancelDownloads() {
        ProcessResult pr = new ProcessResult();
        if (tryCancelUpgrade())
            pr.addGenericMessage("common.cancelled");
        else
            pr.addGenericMessage("modules.versionCheck.notRunning");
        return pr;
    }
    

    /**
     * Try and Cancel the Upgrade
     * @return true if cancelled, false if not running
     */
    public static boolean tryCancelUpgrade(){
        synchronized (UPGRADE_DOWNLOADER_LOCK) {
            if (UPGRADE_DOWNLOADER == null)
                return false;
            else {
                UPGRADE_DOWNLOADER.cancel();
                return true;
            }
        }
    }
    
    @DwrPermission(admin = true)
    public static ProcessResult monitorDownloads() {
        ProcessResult result = new ProcessResult();
        synchronized (UPGRADE_DOWNLOADER_LOCK) {
            if (UPGRADE_DOWNLOADER == null && stage == UpgradeState.IDLE) {
                result.addGenericMessage("modules.versionCheck.notRunning");
                return result;
            }
            result.addData("finished", finished);
            result.addData("cancelled", cancelled);
            result.addData("restart", restart);
            if (error != null)
                result.addData("error", error);
            result.addData("stage", stage);
            result.addData("results", getUpgradeResults(Common.getTranslations()));
            
            if(finished)
                stage = UpgradeState.IDLE;
        }

        return result;
    }

    /**
     * How many upgrades are available
     * @return
     * @throws Exception
     */
    public static int upgradesAvailable() throws Exception {
        JsonValue jsonResponse = getAvailableUpgrades();

        if (jsonResponse == null)
            return 0; //Empty store.url
        if (jsonResponse instanceof JsonString)
            throw new Exception("Mango Store Response Error: " + jsonResponse.toString());

        JsonObject root = jsonResponse.toJsonObject();

        int size = root.getJsonArray("upgrades").size();
        if (size > 0) {
            // Notify the listeners
            JsonValue jsonUpgrades = root.get("upgrades");
            JsonArray jsonUpgradesArray = jsonUpgrades.toJsonArray();
            for (JsonValue v : jsonUpgradesArray) {
                for (ModuleNotificationListener l : listeners)
                    l.moduleUpgradeAvailable(v.getJsonValue("name").toString(),
                            v.getJsonValue("version").toString());
            }
            JsonValue jsonInstalls = root.get("newInstalls");
            JsonArray jsonInstallsArray = jsonInstalls.toJsonArray();
            for (JsonValue v : jsonInstallsArray) {
                for (ModuleNotificationListener l : listeners)
                    l.newModuleAvailable(v.getJsonValue("name").toString(),
                            v.getJsonValue("version").toString());
            }

        }
        return size;
    }

    public static JsonValue getAvailableUpgrades() throws JsonException, IOException, HttpException {
        // Create the request
        List<Module> modules = ModuleRegistry.getModules();
        Module.sortByName(modules);

        Map<String, Object> json = new HashMap<>();
        json.put("guid", Providers.get(ICoreLicense.class).getGuid());
        json.put("description", SystemSettingsDao.instance.getValue(SystemSettingsDao.INSTANCE_DESCRIPTION));
        json.put("distributor", Common.envProps.getString("distributor"));
        json.put("upgradeVersionState",
                SystemSettingsDao.instance.getIntValue(SystemSettingsDao.UPGRADE_VERSION_STATE));

        Properties props = new Properties();
        File propFile = new File(Common.MA_HOME + File.separator + "release.properties");
        int versionState = UpgradeVersionState.DEVELOPMENT;
        if (propFile.exists()) {
            InputStream in = new FileInputStream(propFile);
            try {
                props.load(in);
            } finally {
                in.close();
            }
            String currentVersionState = props.getProperty("versionState");
            try {
                if (currentVersionState != null)
                    versionState = Integer.valueOf(currentVersionState);
            } catch (NumberFormatException e) {
            }
        }
        json.put("currentVersionState", versionState);

        Map<String, String> jsonModules = new HashMap<>();
        json.put("modules", jsonModules);

        Version coreVersion = Common.getVersion();
        jsonModules.put("core", coreVersion.toString());
        for (Module module : modules)
            if (!module.isMarkedForDeletion())
                jsonModules.put(module.getName(), module.getVersion().toString());

        // Add in the unloaded modules so we don't re-download them if we don't have to
        for (Module module : ModuleRegistry.getUnloadedModules())
            if (!module.isMarkedForDeletion())
                jsonModules.put(module.getName(), module.getVersion().toString());

        StringWriter stringWriter = new StringWriter();
        new JsonWriter(Common.JSON_CONTEXT, stringWriter).writeObject(json);
        String requestData = stringWriter.toString();

        // Send the request
        String baseUrl = Common.envProps.getString("store.url");
        if(StringUtils.isEmpty(baseUrl)) {
            LOG.info("No version check performed as no store.url is defined in env.properties.");
            return null;
        }
        baseUrl += "/servlet/versionCheck";

        HttpPost post = new HttpPost(baseUrl);
        post.setEntity(new StringEntity(requestData));

        String responseData = HttpUtils4.getTextContent(Common.getHttpClient(), post, 1);

        // Parse the response
        JsonTypeReader jsonReader = new JsonTypeReader(responseData);
        return jsonReader.read();
    }

    public static class UpgradeDownloader extends HighPriorityTask {
    	
        private final List<StringStringPair> modules;
        private final boolean backup;
        private final boolean restart;
        private final File coreDir = new File(Common.MA_HOME);
        private final File moduleDir = new File(coreDir, Constants.DIR_WEB + "/" + Constants.DIR_MODULES);
        private volatile boolean cancelled = false;
        private User user;

        /**
         * Only allow 1 to be scheduled all others will be rejected
         * @param modules
         * @param backup
         * @param restart
         */
        public UpgradeDownloader(List<StringStringPair> modules, boolean backup, boolean restart, User user) {
        	super("Upgrade downloader", "UpgradeDownloader", 0);
            this.modules = modules;
            this.backup = backup;
            this.restart = restart;
            this.user = user;
        }
        
        @Override
        public boolean cancel() {
            this.cancelled = true;
            return super.cancel();
        }

        @Override
        public void run(long runtime) {
            try {
                for (ModuleNotificationListener listener : listeners)
                    listener.upgradeStateChanged(UpgradeState.STARTED);
                LOG.info("UpgradeDownloader started");
                String baseStoreUrl = Common.envProps.getString("store.url");
                if(StringUtils.isEmpty(baseStoreUrl)) {
                    LOG.info("Upgrade download not started as store.url is blank in env.properties.");
                    for(ModuleNotificationListener listener : listeners)
                        listener.upgradeStateChanged(UpgradeState.DONE);
                    return;
                }
    
                if (backup) {
                    // Run the backup.
                    LOG.info("UpgradeDownloader: " + UpgradeState.BACKUP);
                    for (ModuleNotificationListener listener : listeners)
                        listener.upgradeStateChanged(UpgradeState.BACKUP);
    
                    // Do the backups. They run async, so this returns immediately. The shutdown will
                    // wait for the
                    // background processes to finish though.
                    BackupWorkItem.queueBackup(
                            SystemSettingsDao.instance.getValue(SystemSettingsDao.BACKUP_FILE_LOCATION));
                    DatabaseBackupWorkItem.queueBackup(SystemSettingsDao.instance
                            .getValue(SystemSettingsDao.DATABASE_BACKUP_FILE_LOCATION));
                }

                LOG.info("UpgradeDownloader: " + UpgradeState.DOWNLOAD);
                for (ModuleNotificationListener listener : listeners)
                    listener.upgradeStateChanged(UpgradeState.DOWNLOAD);
    
                HttpClient httpClient = Common.getHttpClient();
    
                // Create the temp directory into which to download, if necessary.
                File tempDir = new File(Common.MA_HOME, ModuleUtils.DOWNLOAD_DIR);
                if (!tempDir.exists())
                    tempDir.mkdirs();
    
                // Delete anything that is currently the temp directory.
                try {
                    FileUtils.cleanDirectory(tempDir);
                } catch (IOException e) {
                    String error = "Error while clearing temp dir: " + e.getMessage();
                    LOG.warn("Error while clearing temp dir", e);
                    for (ModuleNotificationListener listener : listeners)
                        listener.upgradeError(error);
                    return;
                }
    
                // Delete all upgrade files of any version from the target directories. Only do this for
                // names in the
                // modules list so that custom modules do not get deleted.
                cleanDownloads();
    
                for (StringStringPair mod : modules) {
                    if (cancelled) {
                        LOG.info("UpgradeDownloader: Cancelled");
                        try {
                            FileUtils.cleanDirectory(tempDir);
                        } catch (IOException e) {
                            String error = "Error while clearing temp dir when cancelled: " + e.getMessage();
                            LOG.warn("Error while clearing temp dir when cancelled", e);
                            for (ModuleNotificationListener listener : listeners)
                                listener.upgradeError(error);
                        }
                        for (ModuleNotificationListener listener : listeners)
                            listener.upgradeStateChanged(UpgradeState.CANCELLED);
                        return;
                    }
                    String name = mod.getKey();
                    Version version = Version.valueOf(mod.getValue());
    
                    String filename = ModuleUtils.moduleFilename(name, version.getNormalVersion());
                    String url = baseStoreUrl + "/"
                            + ModuleUtils.downloadFilename(name, version.getNormalVersion());
                    HttpGet get = new HttpGet(url);
    
                    FileOutputStream out = null;
                    File outFile = new File(tempDir, filename);
                    try {
                        out = new FileOutputStream(outFile);
                        HttpUtils4.execute(httpClient, get, out);
                        for (ModuleNotificationListener listener : listeners)
                            listener.moduleDownloaded(name, version.toString());
                    } catch (IOException e) {
                        LOG.warn("Upgrade download error", e);
                        String error = new TranslatableMessage("modules.downloadFailure", e.getMessage()).translate(Common.getTranslations());
                        //Notify of the failure
                        for (ModuleNotificationListener listener : listeners)
                            listener.moduleDownloadFailed(name, version.toString(), error);
                        return;
                    } finally {
                        try {
                            if (out != null)
                                out.close();
                        } catch (IOException e) {
                            // no op
                        }
                    }
                    
                    //Validate that module.properties exists and is properly readable
                    if(!"core".equals(name)) {
                        Map<String, String> expectedProperties = new HashMap<String, String>();
                        expectedProperties.put("name", name);
                        expectedProperties.put("version", version.toString());
                        boolean signed = true;
                        try (ZipFile module = new ZipFile(outFile)){
                            ZipEntry propertiesFile = module.getEntry(ModuleUtils.Constants.MODULE_SIGNED);
                            if(propertiesFile == null) {
                                signed = false;
                                propertiesFile = module.getEntry(ModuleUtils.Constants.MODULE_PROPERTIES);
                                if(propertiesFile == null) {
                                    LOG.warn("Module missing properties file.");
                                    throw new ShouldNeverHappenException("Module missing properties file.");
                                }
                            }
                            InputStream in = module.getInputStream(propertiesFile);
                            Common.verifyProperties(in, signed, expectedProperties);
                        } catch(Exception e) {
                            LOG.warn(e);
                            throw new ShouldNeverHappenException(e);
                        }
                    }
                }

                LOG.info("UpgradeDownloader: " + UpgradeState.INSTALL);
                for (ModuleNotificationListener listener : listeners)
                    listener.upgradeStateChanged(UpgradeState.INSTALL);
    
                // Move the downloaded files to their proper places.
                File[] files = tempDir.listFiles();
                if (files == null || files.length == 0) {
                    String error = "Weird, there are no downloaded files to move";
                    LOG.warn(error);
                    for (ModuleNotificationListener listener : listeners)
                        listener.upgradeError(error);
                    return;
                }
    
                for (File file : files) {
                    File targetDir =
                            file.getName().startsWith(ModuleUtils.Constants.MODULE_PREFIX + "core")
                                    ? coreDir
                                    : moduleDir;
                    try {
                        FileUtils.moveFileToDirectory(file, targetDir, false);
                    } catch (IOException e) {
                        // If anything bad happens during the copy, try to clean out the download files
                        // again.
                        String error = e.getMessage();
                        for (ModuleNotificationListener listener : listeners)
                            listener.upgradeError(error);
                        cleanDownloads();
                        LOG.warn(e);
                        throw new ShouldNeverHappenException(e);
                    }
                }
    
                // Delete the download dir.
                try {
                    FileUtils.deleteDirectory(tempDir);
                } catch (IOException e) {
                    LOG.warn(e);
                }
    
                // Final chance to be cancelled....
                if (cancelled) {
                    // Delete what we downloaded.
                    cleanDownloads();
                    LOG.info("UpgradeDownloader: Cancelled");
                    for (ModuleNotificationListener listener : listeners)
                        listener.upgradeStateChanged(UpgradeState.CANCELLED);
                    return;
                }
    
                if (restart) {
                    LOG.info("UpgradeDownloader: " + UpgradeState.RESTART);
                    synchronized (SHUTDOWN_TASK_LOCK) {
                        if (SHUTDOWN_TASK == null) {
                            IMangoLifecycle lifecycle = Providers.get(IMangoLifecycle.class);
                            SHUTDOWN_TASK = lifecycle.scheduleShutdown(5000, true, user);
                        }
                    }
                    for (ModuleNotificationListener listener : listeners)
                        listener.upgradeStateChanged(UpgradeState.RESTART);
    
                } else {
                    LOG.info("UpgradeDownloader: " + UpgradeState.DONE);
                    for (ModuleNotificationListener listener : listeners)
                        listener.upgradeStateChanged(UpgradeState.DONE);
                }
            }finally {
                for (ModuleNotificationListener listener : listeners) {
                    try { listener.upgradeTaskFinished(); } catch(Exception e) { }
                }
                synchronized (UPGRADE_DOWNLOADER_LOCK) {
                    UPGRADE_DOWNLOADER = null;
                }       
            }
        }

        private void cleanDownloads() {
            for (StringStringPair mod : modules) {
                final String name = mod.getKey();
                File targetDir = name.equals("core") ? coreDir : moduleDir;
                File[] files = targetDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile() && file.getName().startsWith(ModuleUtils.Constants.MODULE_PREFIX + name))
                            file.delete();
                    }
                }
            }
        }
    }
    
    public static void addModuleNotificationListener(ModuleNotificationListener listener){
        listeners.add(listener);
    }
    public static void removeModuleNotificationListener(ModuleNotificationListener listener){
        listeners.remove(listener);
    }
    
    //For status about upgrade state (Preferably use your own listener)
    protected static UpgradeState stage = UpgradeState.IDLE;
    protected static boolean cancelled;
    protected static boolean finished;
    protected static boolean restart;
    protected static String error = null;
    protected static final List<StringStringPair> moduleResults = new ArrayList<>();
    
    protected static void resetUpgradeStatus() {
        cancelled = false;
        finished = false;
        restart = false;
        error = null;
        moduleResults.clear();
    }
    
    public static List<StringStringPair> getUpgradeResults(Translations translations) {
        synchronized (moduleResults) {
            return new ArrayList<>(moduleResults);
        }
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#moduleDownloaded(java.lang.String, java.lang.String)
     */
    @Override
    public void moduleDownloaded(String name, String version) {
        synchronized (moduleResults) {
            moduleResults.add(new StringStringPair(name, Common.translate("modules.downloadComplete")));
        }
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#moduleDownloadFailed(java.lang.String, java.lang.String)
     */
    @Override
    public void moduleDownloadFailed(String name, String version, String reason) {
        synchronized (moduleResults) {
            moduleResults.add(new StringStringPair(name, reason));
        }
    }
    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#moduleUpgradeAvailable(java.lang.String, java.lang.String)
     */
    @Override
    public void moduleUpgradeAvailable(String name, String version) {
        //No-op
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#upgradeStateChanged(com.serotonin.m2m2.module.ModuleNotificationListener.UpgradeState)
     */
    @Override
    public void upgradeStateChanged(UpgradeState state) {
        stage = state;
        switch(stage) {
            case CANCELLED:
                cancelled = true;
            break;
            case RESTART:
                restart = true;
            break;
            default:
                break;
        }
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#upgradeError(java.lang.String)
     */
    @Override
    public void upgradeError(String e) {
        error = e;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#upgradeTaskFinished()
     */
    @Override
    public void upgradeTaskFinished() {
        finished = true;
    }

    /* (non-Javadoc)
     * @see com.serotonin.m2m2.module.ModuleNotificationListener#newModuleAvailable(java.lang.String, java.lang.String)
     */
    @Override
    public void newModuleAvailable(String name, String version) {
        //no-op
    }
    
}
