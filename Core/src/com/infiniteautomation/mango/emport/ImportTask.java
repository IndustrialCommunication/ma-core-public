/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.infiniteautomation.mango.emport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.infiniteautomation.mango.spring.service.DataPointService;
import com.infiniteautomation.mango.spring.service.DataSourceService;
import com.infiniteautomation.mango.spring.service.EventHandlerService;
import com.infiniteautomation.mango.spring.service.MailingListService;
import com.infiniteautomation.mango.spring.service.PublisherService;
import com.infiniteautomation.mango.spring.service.UsersService;
import com.infiniteautomation.mango.util.ConfigurationExportData;
import com.serotonin.json.JsonException;
import com.serotonin.json.JsonReader;
import com.serotonin.json.type.JsonArray;
import com.serotonin.json.type.JsonObject;
import com.serotonin.json.type.JsonValue;
import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.i18n.ProcessResult;
import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.module.EmportDefinition;
import com.serotonin.m2m2.module.ModuleRegistry;
import com.serotonin.m2m2.util.BackgroundContext;
import com.serotonin.m2m2.util.timeout.ProgressiveTask;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.event.AbstractEventHandlerVO;
import com.serotonin.m2m2.vo.event.detector.AbstractPointEventDetectorVO;
import com.serotonin.m2m2.vo.hierarchy.PointFolder;
import com.serotonin.m2m2.vo.permission.PermissionHolder;
import com.serotonin.m2m2.vo.publish.PublishedPointVO;
import com.serotonin.util.ProgressiveTaskListener;

/**
 * @author Matthew Lohbihler
 */
public class ImportTask<DS extends DataSourceVO<DS>, PUB extends PublishedPointVO, EH extends AbstractEventHandlerVO<EH>> extends ProgressiveTask {
	
	private static Log LOG = LogFactory.getLog(ImportTask.class);
	
    protected final ImportContext importContext;
    protected final PointHierarchyImporter hierarchyImporter;
    protected final PermissionHolder user;
    protected float progress = 0f;
    protected float progressChunk;
    
    protected final List<Importer> importers = new ArrayList<Importer>();
    protected final List<ImportItem> importItems = new ArrayList<ImportItem>();
    protected final List<DataPointSummaryPathPair> dpPathPairs = new ArrayList<DataPointSummaryPathPair>();
    protected final Map<String, DataPointVO> eventDetectorPoints = new HashMap<String, DataPointVO>();
    
    protected final DataPointService dataPointService;
    /**
     * Create an Import task with a listener to be scheduled now
     * @param root
     * @param translations
     * @param user
     * @param listener
     */
    public ImportTask(JsonObject root, 
            Translations translations, 
            PermissionHolder user,
            UsersService usersService,
            MailingListService mailingListService,
            DataSourceService<DS> dataSourceService,
            DataPointService dataPointService,
            PublisherService<PUB> publisherService,
            EventHandlerService<EH> eventHandlerService,
            ProgressiveTaskListener listener, boolean schedule) {
    	super("JSON import task", "JsonImport", 10, listener);
    	this.dataPointService = dataPointService;
        JsonReader reader = new JsonReader(Common.JSON_CONTEXT, root);
        this.importContext = new ImportContext(reader, new ProcessResult(), translations);
        
        Objects.requireNonNull(user);
        this.user = user;

        for (JsonValue jv : nonNullList(root, ConfigurationExportData.USERS))
            addImporter(new UserImporter(jv.toJsonObject(), usersService, user));
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.DATA_SOURCES))
            addImporter(new DataSourceImporter<DS>(jv.toJsonObject(), dataSourceService, user));
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.DATA_POINTS))
            addImporter(new DataPointImporter<DS>(jv.toJsonObject(), dataPointService, dataSourceService, user, dpPathPairs));
        
        JsonArray phJson = root.getJsonArray(ConfigurationExportData.POINT_HIERARCHY);
        if(phJson != null) {
            hierarchyImporter = new PointHierarchyImporter(phJson, user);
        	    addImporter(hierarchyImporter);
        } else
            hierarchyImporter = null;
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.MAILING_LISTS))
            addImporter(new MailingListImporter(jv.toJsonObject(), mailingListService, user));
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.PUBLISHERS))
            addImporter(new PublisherImporter<PUB>(jv.toJsonObject(), publisherService, user));
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.EVENT_HANDLERS))
            addImporter(new EventHandlerImporter<EH>(jv.toJsonObject(), eventHandlerService, user));
        
        JsonObject obj = root.getJsonObject(ConfigurationExportData.SYSTEM_SETTINGS);
        if(obj != null)
            addImporter(new SystemSettingsImporter(obj, user));
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.TEMPLATES))
            addImporter(new TemplateImporter(jv.toJsonObject(), user));
        
        for (JsonValue jv : nonNullList(root, ConfigurationExportData.VIRTUAL_SERIAL_PORTS))
            addImporter(new VirtualSerialPortImporter(jv.toJsonObject(), user));
        
        for(JsonValue jv : nonNullList(root, ConfigurationExportData.JSON_DATA))
        	addImporter(new JsonDataImporter(jv.toJsonObject(), user));
        
        for (EmportDefinition def : ModuleRegistry.getDefinitions(EmportDefinition.class)) {
            ImportItem importItem = new ImportItem(def, root.get(def.getElementId()));
            importItems.add(importItem);
        }
        
        for(JsonValue jv : nonNullList(root, ConfigurationExportData.EVENT_DETECTORS)) 
        	addImporter(new EventDetectorImporter(jv.toJsonObject(), user, eventDetectorPoints));

        //Quick hack to ensure the Global Scripts are imported first in case they are used in scripts that will be loaded during this import
        final String globalScriptId = "sstGlobalScripts";
        Iterator<ImportItem> it = importItems.iterator();
        while(it.hasNext()) {
            ImportItem item = it.next();
            if(globalScriptId.equals(item.getEmportDefinition().getElementId())) {
                it.remove();
                Importer importer = new Importer(root, user) {
                    
                    private ImportItem gsImporter = item;
                    
                    @Override
                    protected void importImpl() {
                        try {
                            if (!gsImporter.isComplete())
                                gsImporter.importNext(ctx, user);
                        } catch (JsonException e) {
                            addException(e);
                        }
                    }
                };
                importer.setImportContext(importContext);
                importer.setImporters(importers);
                importers.add(0, importer);
                break;
            }
        }
        
        this.progressChunk = 100f/((float)importers.size() + (float)importItems.size() + 1);  //+1 for processDataPointPaths 

        if(schedule)    
            Common.backgroundProcessing.execute(this);
    }

    private List<JsonValue> nonNullList(JsonObject root, String key) {
        JsonArray arr = root.getJsonArray(key);
        if (arr == null)
            arr = new JsonArray();
        return arr;
    }

    private void addImporter(Importer importer) {
        importer.setImportContext(importContext);
        importer.setImporters(importers);
        importers.add(importer);
    }

    public ProcessResult getResponse() {
        return importContext.getResult();
    }

    protected int importerIndex;
    protected boolean importerSuccess;
    protected boolean importedItems;

    @Override
    protected void runImpl() {
        try {
            BackgroundContext.set(user);

            if (!importers.isEmpty()) {
                if (importerIndex >= importers.size()) {
                    // A run through the importers has been completed.
                    if (importerSuccess) {
                        // If there were successes with the importers and there are still more to do, run through 
                        // them again.
                        importerIndex = 0;
                        importerSuccess = false;
                    } else if(!importedItems) {
        	            try {
                            for (ImportItem importItem : importItems) {
                                if (!importItem.isComplete()) {
                                    importItem.importNext(importContext, user);
                                    return;
                                }
                            }
                            importedItems = true;   // We may have imported a dependency in a module
                            importerIndex = 0;
                        }
                        catch (Exception e) {
                            addException(e);
                        }
                    } else {
                        // There are importers left in the list, but there were no successful imports in the last run
                        // of the set. So, all that is left is stuff that will always fail. Copy the validation 
                        // messages to the context for each.
                    	    // Run the import items.
                        for (Importer importer : importers)
                            importer.copyMessages();
                        importers.clear();
                        processDataPointPaths(hierarchyImporter, dpPathPairs);
                        completed = true;
                        return;
                    }
                }

                // Run the next importer
                Importer importer = importers.get(importerIndex);
                try {
                    importer.doImport();
                    if (importer.success()) {
                        // The import was successful. Note the success and remove the importer from the list.
                        importerSuccess = true;
                        importers.remove(importerIndex);
                    }
                    else{
                        // The import failed. Leave it in the list since the run of another importer
                        // may resolved the problem.
                        importerIndex++;
                    }
                }
                catch (Exception e) {
                    // Uh oh...
                	LOG.error(e.getMessage(),e);
                    addException(e);
                    importers.remove(importerIndex);
                }

                return;
            }

            // Run the import items.
            try {
                for (ImportItem importItem : importItems) {
                    if (!importItem.isComplete()) {
                        importItem.importNext(importContext, user);
                        return;
                    }
                }
                processDataPointPaths(hierarchyImporter, dpPathPairs);
                processUpdatedDetectors(eventDetectorPoints);
                completed = true;
            }
            catch (Exception e) {
                addException(e);
            }
        }
        finally {
            BackgroundContext.remove();
            //Compute progress, but only declare if we are < 100 since we will declare 100 when done
            //Our progress is 100 - chunk*importersLeft
            int importItemsLeft = 1;
            if(completed)
                importItemsLeft = 0; //Since we know we ran the processDataPointPaths method
            for(ImportItem item : importItems)
                if(!item.isComplete())
                    importItemsLeft++;
            this.progress = 100f - progressChunk*((float)importers.size() + (float)importItemsLeft);
            if(progress < 100f)
                declareProgress(this.progress);
        }
    }
    
    public void processDataPointPaths(PointHierarchyImporter hierarchyImporter, List<DataPointSummaryPathPair> dpPathPairs) {

        PointFolder root;
        if(hierarchyImporter != null && hierarchyImporter.getHierarchy() != null) 
            root = hierarchyImporter.getHierarchy().getRoot();
        else if(dpPathPairs.size() > 0)
            root = DataPointDao.getInstance().getPointHierarchy(false).getRoot();
        else
            return;
        
        String pathSeparator = SystemSettingsDao.instance.getValue(SystemSettingsDao.HIERARCHY_PATH_SEPARATOR);
        for(DataPointSummaryPathPair dpp : dpPathPairs) {
            root.removePointRecursively(dpp.getDataPointSummary().getId());
            PointFolder starting = root;
            PointFolder previous = root;
            String[] pathParts = dpp.getPath().split(pathSeparator);
            
            if(pathParts.length == 1 && StringUtils.isBlank(pathParts[0])) { //Check if it's in the root
                root.getPoints().add(dpp.getDataPointSummary());
                continue;
            }
            
            for(String s : pathParts) {
                if(StringUtils.isBlank(s))
                    continue;
                previous = starting;
                starting = starting.getSubfolder(s);
                if(starting == null) {
                    PointFolder newFolder = new PointFolder();
                    newFolder.setName(s);
                    previous.addSubfolder(newFolder);
                    starting = newFolder;
                }
            }

            starting.addDataPoint(dpp.getDataPointSummary());
        }
        DataPointDao.getInstance().savePointHierarchy(root);
        importContext.addSuccessMessage(false, "emport.pointHierarchy.prefix", "");
    }
    
    private void processUpdatedDetectors(Map<String, DataPointVO> eventDetectorMap) {
        for(DataPointVO dpvo : eventDetectorMap.values()) {
            //We can't really guarantee that the import didnt' also ocntain event detectors on the point or that the
            // Data Point VO's we retrieved are still correct, so mix in the detectors to the point out of the database.
            boolean isNew = true;
            DataPointVO saved = DataPointDao.getInstance().getDataPoint(dpvo.getId(), false);
            DataPointDao.getInstance().setEventDetectors(saved);
            for(AbstractPointEventDetectorVO<?> eventDetector : dpvo.getEventDetectors()) {
                Iterator<AbstractPointEventDetectorVO<?>> iter = saved.getEventDetectors().iterator();
                while(iter.hasNext()) {
                    AbstractPointEventDetectorVO<?> next = iter.next();
                    if(eventDetector.getXid().equals(next.getXid())) { //Same detector, replace it
                        eventDetector.setId(next.getId());
                        isNew = false;
                        iter.remove();
                        break;
                    }
                }
                //Having removed the old versions, add the new.
                saved.getEventDetectors().add(eventDetector);
            }
            
            //Save the data point
            dataPointService.update(saved.getId(), saved, user);
            for(AbstractPointEventDetectorVO<?> modified : dpvo.getEventDetectors())
                importContext.addSuccessMessage(isNew, "emport.eventDetector.prefix", modified.getXid());
        }
    }

    private void addException(Exception e) {
        String msg = e.getMessage();
        Throwable t = e;
        while ((t = t.getCause()) != null)
            msg += ", " + importContext.getTranslations().translate("emport.causedBy") + " '" + t.getMessage() + "'";
        //We were missing NPE and others without a msg
        if(msg == null)
        	msg = e.getClass().getCanonicalName();
        importContext.getResult().addGenericMessage("common.default", msg);
    }
}
