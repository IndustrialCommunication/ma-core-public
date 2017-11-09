/*
    Copyright (C) 2014 Infinite Automation Systems Inc. All rights reserved.
    @author Matthew Lohbihler
 */
package com.serotonin.m2m2.module;

/**
 * The base level of a module definition class. In general this class should not be directly extended. Instead, the sub
 * classes should be extended since it is the sub classes that will be recognized and handled upon startup. Other
 * classes will be ignored.
 * 
 * @author Matthew Lohbihler
 */
abstract public class ModuleElementDefinition {
    private Module module;

    /**
     * Access to the module class that owns this definition.
     * 
     * @return the definition's module
     */
    public Module getModule() {
        return module;
    }

    void setModule(Module module) {
        this.module = module;
    }
    
    /**
     * Called immediately after the module is loaded, before the system is initialized.
     * @param install module is being installed for the first time
     * @param upgrade module is being upgraded from a previous version
     */
    public void preInitialize(boolean install, boolean upgrade) {
        // Override as required
    }

    /**
     * Called immediately after the database is initialized, but before the event and runtime managers. Should not be
     * called by client code.
     * @param install module is being installed for the first time
     * @param upgrade module is being upgraded from a previous version
     */
    public void postDatabase(boolean install, boolean upgrade) {
        // Override as required
    }
    
    /**
     * Called after immediately after the event manager is initialized, but before the runtime managers. Should not be
     * called by client code.
     * @param install module is being installed for the first time
     * @param upgrade module is being upgraded from a previous version
     */
    public void postEventManager(boolean install, boolean upgrade) {
        // Override as required
    }
    
    /**
     * Called after the system is initialized, i.e. once services like the database, timer, properties, runtime, etc are
     * available. Should not be called by client code.
     * 
     * @param install module is being installed for the first time
     * @param upgrade module is being upgraded from a previous version
     */
    public void postInitialize(boolean install, boolean upgrade) {
        // Override as required
    }
    
    /**
     * Called before the system is terminated, i.e. while services are still available. Should not
     * be called by client code.
     * @param uninstall module will be uninstalled
     */
    public void preTerminate(boolean uninstall) {
        // Override as required
    }
    
    /**
     * Called upon shutdown after the runtime, but before the event manager, has been terminated. Should not
     * be called by client code.
     * @param uninstall module will be uninstalled
     */
    public void postRuntimeManagerTerminate(boolean uninstall) {
        // Override as required
    }

    /**
     * Called after the system is terminated. Should not
     * be called by client code.
     * @param uninstall module will be uninstalled
     */
    public void postTerminate(boolean uninstall) {
        // Override as required
    }
    
    /**
     * This method is run once at shutdown if the owning module has been marked for deletion. This provides the
     * definition class an opportunity to perform any necessary cleanup, such as dropping database tables, removing
     * files, etc.
     * 
     */
    public void uninstall() {
        // Override as required
    }
}
