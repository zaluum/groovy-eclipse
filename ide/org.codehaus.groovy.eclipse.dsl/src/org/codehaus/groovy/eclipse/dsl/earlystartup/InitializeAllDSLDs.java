/*******************************************************************************
 * Copyright (c) 2011 Codehaus.org, SpringSource, and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Andrew Eisenberg - Initial implemenation
 *******************************************************************************/
package org.codehaus.groovy.eclipse.dsl.earlystartup;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.eclipse.dsl.DSLPreferencesInitializer;
import org.codehaus.groovy.eclipse.dsl.GroovyDSLCoreActivator;
import org.codehaus.groovy.eclipse.dsl.RefreshDSLDJob;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IStartup;
import org.eclipse.ui.preferences.ScopedPreferenceStore;

/**
 * Initializes all DSLD scripts in the workspace on startup
 * This will start the Groovy plugin if any groovy projects are found
 * @author andrew
 * @created Nov 25, 2010
 */
public class InitializeAllDSLDs implements IStartup {
    public void earlyStartup() {
        initializeAll();
    }

    public void initializeAll() {
        IPreferenceStore prefStore = getPreferenceStore();
        if (prefStore.getBoolean(DSLPreferencesInitializer.DSLD_DISABLED)) {
            return;
        }

        
        IProject[] allProjects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        List<IProject> toRefresh = new ArrayList<IProject>(allProjects.length);
        for (IProject project : allProjects) {
            // don't access the GroovyNature class here because we don't want to start
            // the groovy plugin if we don't have to.
            try {
                if (project.isAccessible() && project.hasNature("org.eclipse.jdt.groovy.core.groovyNature")) {
                    toRefresh.add(project);
                }
            } catch (CoreException e) {
                logException(e);
            }
        }
        Job refreshJob = new RefreshDSLDJob(toRefresh);
        refreshJob.setPriority(Job.LONG);
        refreshJob.schedule();
    }

    /**
     * Must keep this in a different method to avoid accidentally starting the DSLD plugin (and hence all of the groovy plugins).
     * @param e
     */
    private void logException(CoreException e) {
        GroovyDSLCoreActivator.logException(e);
    }
    
    static final String PLUGIN_ID = "org.codehaus.groovy.eclipse.dsl";
    
    /**
     * Avoids accidentally loading the plugin
     * @return
     */
    public IPreferenceStore getPreferenceStore() {
        return new ScopedPreferenceStore(new InstanceScope(), PLUGIN_ID);
    }
}