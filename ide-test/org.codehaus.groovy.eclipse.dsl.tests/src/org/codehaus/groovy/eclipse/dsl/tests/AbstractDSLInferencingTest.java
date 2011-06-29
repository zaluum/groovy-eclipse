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
package org.codehaus.groovy.eclipse.dsl.tests;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.codehaus.groovy.activator.GroovyActivator;
import org.codehaus.groovy.eclipse.GroovyLogManager;
import org.codehaus.groovy.eclipse.IGroovyLogger;
import org.codehaus.groovy.eclipse.TraceCategory;
import org.codehaus.groovy.eclipse.core.model.GroovyRuntime;
import org.codehaus.groovy.eclipse.core.util.ReflectionUtils;
import org.codehaus.groovy.eclipse.dsl.DSLDStore;
import org.codehaus.groovy.eclipse.dsl.DSLDStoreManager;
import org.codehaus.groovy.eclipse.dsl.DSLPreferences;
import org.codehaus.groovy.eclipse.dsl.GroovyDSLCoreActivator;
import org.codehaus.groovy.eclipse.dsl.RefreshDSLDJob;
import org.codehaus.groovy.eclipse.dsl.contributions.IContributionGroup;
import org.codehaus.groovy.eclipse.dsl.pointcuts.IPointcut;
import org.codehaus.groovy.eclipse.test.SynchronizationUtils;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IStorage;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.groovy.tests.search.AbstractInferencingTest;

/**
 * 
 * @author Andrew Eisenberg
 * @created Feb 19, 2011
 */
public class AbstractDSLInferencingTest extends AbstractInferencingTest {

    public AbstractDSLInferencingTest(String name) {
        super(name);
    }
    
    class TestLogger implements IGroovyLogger {

        public void log(TraceCategory category, String message) {
            System.out.println(category.getPaddedLabel() + ": " + message);
        }

        public boolean isCategoryEnabled(TraceCategory category) {
            return true;
        }
        
    }
    
    TestLogger logger = new TestLogger();
    
    protected boolean doRemoveClasspathContainer = true;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        GroovyLogManager.manager.addLogger(logger);
        if (doRemoveClasspathContainer) {
            GroovyRuntime.removeClasspathContainer(GroovyDSLCoreActivator.CLASSPATH_CONTAINER_ID, JavaCore.create(project));
        } else {
            GroovyRuntime.addLibraryToClasspath(JavaCore.create(project), GroovyDSLCoreActivator.CLASSPATH_CONTAINER_ID);
            env.fullBuild();
            new RefreshDSLDJob(project).run(null);
        }
        GroovyDSLCoreActivator.getDefault().getContainerListener().ignoreProject(project);
    }
    
    @Override
    protected void tearDown() throws Exception {
        GroovyLogManager.manager.removeLogger(logger);
        defaultFileExtension = "groovy";
        super.tearDown();
    }

    protected String[] createDsls(String ... dsls) {
        return createDsls(0, dsls);
    }
    protected String[] createDsls(int startWith, String ... dsls) {
        int i = startWith;
        System.out.println("Now creating " + dsls.length + " DSLD files.");
        for (String dsl : dsls) {
            System.out.println("Creating:\n" + dsl + "\n");
            IPath path = env.addFile(project.getFullPath(), "dsl" + i++ + ".dsld", dsl);
            IFile file = env.getWorkspace().getRoot().getFile(path);
            if (!file.exists()) {
                fail("File " + file + " just created, but doesn't exist");
            }
        }
        return dsls;
    }
    
    protected void deleteDslFile(int fileNum) {
        env.removeFile(project.getFile("dsl" + fileNum + ".dsld").getFullPath());
    }

    protected String[] createDSLsFromFiles(String ... fileNames) throws IOException {
        String[] dslContents = new String[fileNames.length];
        for (int i = 0; i < fileNames.length; i++) {
            dslContents[i] = GroovyDSLDTestsActivator.getDefault().getTestResourceContents(fileNames[i]);
        }
        return createDsls(dslContents);
    }
    
    protected void addJarToProject(String jarName) throws JavaModelException, IOException {
        String externalFilePath = findExternalFilePath(jarName);
        env.addExternalJar(project.getFullPath(), externalFilePath);
    }

    /**
     * @param jarName
     * @return
     * @throws MalformedURLException
     * @throws IOException
     */
    protected String findExternalFilePath(String jarName)
            throws MalformedURLException, IOException {
        URL url = GroovyDSLDTestsActivator.getDefault().getTestResourceURL(jarName);
        URL resolved = FileLocator.resolve(url);
        String externalFilePath = resolved.getFile();
        return externalFilePath;
    }
    
    protected void removeJarFromProject(String jarName) throws JavaModelException, IOException {
        URL url = GroovyDSLDTestsActivator.getDefault().getTestResourceURL(jarName);
        URL resolved = FileLocator.resolve(url);
        env.removeExternalJar(project.getFullPath(), new Path(resolved.getFile()));
    }
    
    
    
    /**
     * @param expectedNumDslFiles  number of dsl files currently registered
     * @param allExpectedPointcuts map: dsl file name -> all pointcuts in that file 
     * @param expectedContributionCounts map: pointcut name -> all contribution group associated with
     */
    protected void assertDSLStore(int expectedNumDslFiles, Map<String, List<String>> allExpectedPointcuts, Map<String, Integer> expectedContributionCounts) {
        
        // ensure DSLDs are refreshed
        // don't schedule. instead run in the same thread.
        System.out.println("About to run RefreshDSLDJob");
        // ensure this classpath container is gone
        GroovyRuntime.removeClasspathContainer(GroovyDSLCoreActivator.CLASSPATH_CONTAINER_ID, JavaCore.create(project));
        env.fullBuild();
        RefreshDSLDJob job = new RefreshDSLDJob(project);
        job.run(new NullProgressMonitor());
        System.out.println("Finished RefreshDSLDJob");
        
        
        DSLDStoreManager manager = GroovyDSLCoreActivator.getDefault().getContextStoreManager();
        DSLDStore store = manager.getDSLDStore(project);
        Set<String> disabledScripts = DSLPreferences.getDisabledScriptsAsSet();

        IStorage[] keys = store.getAllContextKeys();
        Arrays.sort(keys, new Comparator<IStorage>() {
            public int compare(IStorage o1, IStorage o2) {
                return o1.getFullPath().toPortableString().compareTo(o2.getFullPath().toPortableString());
            }
        });
        assertEquals(expectedNumDslFiles, keys.length);
        int i = 0;
        for (IStorage key : keys) {
            String uniqueString = DSLDStore.toUniqueString(key);
            // don't check the name for external and binary dslds
            if (key instanceof IFile) {
                assertEquals(project.getFullPath() + "/dsl" + i++ + ".dsld", uniqueString);
            }            
            // check to see if the file is disabled.
            if (disabledScripts.contains(uniqueString)) {
                continue;
            }
            
            // now check the pointcuts in this script
            Set<IPointcut> pcs = ((Map<IStorage, Set<IPointcut>>) ReflectionUtils.getPrivateField(DSLDStore.class, "keyContextMap", store)).get(key);
            List<String> expectedPcs = allExpectedPointcuts.get(uniqueString);
            for (IPointcut pc : pcs) {
                assertTrue("Didn't find expected Pointcut " + pc + " in\n" + expectedPcs, expectedPcs.contains(createSemiUniqueName(pc)));
                
                // now check the contributions for each pointcut
                List<IContributionGroup> group = ((Map<IPointcut, List<IContributionGroup>>) ReflectionUtils.getPrivateField(DSLDStore.class, "pointcutContributionMap", store)).get(pc);
                int groupSize = group.size();
                int expectedSize = expectedContributionCounts.get(createSemiUniqueName(pc));
                assertEquals("Didn't find expected number of contributions for " + pc, expectedSize, groupSize);
            }
            assertEquals("Wrong number of pointcuts in store:\nExpected: " + expectedPcs + "\nActual: " + pcs, expectedPcs.size(), pcs.size());
        }
    }
    protected Map<String, Integer> createExpectedContributionCount(String[] pcs, Integer[] counts) {
        Map<String, Integer> map = new HashMap<String, Integer>();
        for (int i = 0; i < counts.length; i++) {
            map.put(pcs[i], counts[i]);
        }
        return map;
    }

    protected Map<String, List<String>> createExpectedPointcuts(String[]... pointcuts) {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        int i = 0;
        for (String[] strings : pointcuts) {
            String name = DSLDStore.toUniqueString(project.getFile("dsl" + i++ + ".dsld"));
            map.put(name, Arrays.asList(strings));
        }
        return map;
    }

    protected Map<String, List<String>> createExpectedPointcuts(IStorage[] storages, String[]... pointcuts) {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        int i = 0;
        for (String[] strings : pointcuts) {
            String name = DSLDStore.toUniqueString(storages[i++]);
            map.put(name, Arrays.asList(strings));
        }
        return map;
    }
    
    protected String createSemiUniqueName(IPointcut pc) {
        return pc.getClass().getName() + ":" + pc.getContainerIdentifier().getFullPath().lastSegment();
    }
    
    protected String createSemiUniqueName(Class<? extends IPointcut> pc, int cnt) {
        return pc.getName() + ":" + "dsl" + cnt + ".dsld";
    }
    
    protected String createSemiUniqueName(Class<? extends IPointcut> pc, IStorage storage) {
        return pc.getName() + ":" + DSLDStore.toUniqueString(storage);
    }
    
    
    
    protected void assertDSLType(String contents, String name) {
        assertDeclaringType(contents, contents.indexOf(name), contents.indexOf(name) + name.length(), "p.IPointcut", true);
    }
    
    protected void assertUnknownDSLType(String contents, String name) {
        assertUnknownConfidence(contents, contents.indexOf(name), contents.indexOf(name) + name.length(), "Search", true);
    }
}