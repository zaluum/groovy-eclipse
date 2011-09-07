package org.codehaus.groovy.eclipse.dsl.checker;

import java.io.File;

import org.codehaus.groovy.eclipse.dsl.RefreshDSLDJob;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.application.WorkbenchAdvisor;
import org.eclipse.ui.internal.Workbench;

public class StaticCheckerApplication implements IApplication {
    /**
     * 
     * @author andrew
     * @created Aug 31, 2011
     */
    public class CheckerWorkbenchAdvisor extends WorkbenchAdvisor {
    
        @Override
        public String getInitialWindowPerspectiveId() {
            return null;
        }
        
        
        @Override
        public void postStartup() {
            // Add the extra dslds
            addExtraDslds();
            
            // now ensure that the classpath containers and variables are initialized
            try {
                JavaCore.initializeAfterLoad(new NullProgressMonitor());
            } catch (CoreException e1) {
                e1.printStackTrace();
            }
            
            // Ensure that dslds are all available
            RefreshDSLDJob job = new RefreshDSLDJob(ResourcesPlugin.getWorkspace().getRoot().getProject(projectName));
            job.run(new NullProgressMonitor());

            
            System.out.println("Performing static type checking on project " + projectName);
            IStaticCheckerHandler handler = new SysoutStaticCheckerHandler();
            ResourceTypeChecker checker = new ResourceTypeChecker(handler, projectName, inclusionFilters, exclusionFilters, assertionsOnly);
            try {
                checker.doCheck(null);
            } catch (CoreException e) {
                e.printStackTrace();
            } finally {
                removeExtraDslds();
            }
            Workbench.getInstance().close();
        }
    }

    private String projectName;
    private char[][] inclusionFilters;
    private char[][] exclusionFilters;
    private boolean assertionsOnly;
    private String[] extraDslds;
    private IFile[] extraDsldFiles;
    
    public Object start(IApplicationContext context) throws Exception {
        processCommandLine((String[]) context.getArguments().get(IApplicationContext.APPLICATION_ARGS));
        Display display = createDisplay();
        WorkbenchAdvisor advisor = new CheckerWorkbenchAdvisor();
        return PlatformUI.createAndRunWorkbench(display, advisor);
    }

    public void stop() {
        removeExtraDslds();
    }

    private void addExtraDslds() {
        if (extraDslds != null) {
            extraDsldFiles = new IFile[extraDslds.length];
            for (int i = 0; i < extraDslds.length; i++) {
                File file = new File(extraDslds[i]);
                if (file.exists()) {
                    IFile linkedFile = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName).getFile(file.getName());
                    if (linkedFile.exists() && linkedFile.isLinked()) {
                        try {
                            linkedFile.delete(true, null);
                        } catch (CoreException e) {
                            e.printStackTrace();
                        }
                    }
                    if (!linkedFile.exists()) {
                        try {
                            System.out.println("Adding " + file.toURI());
                            linkedFile.createLink(file.toURI(), IResource.NONE, null);
                            extraDsldFiles[i] = linkedFile;
                        } catch (CoreException e) {
                            e.printStackTrace();
                        }
                    }
                } else {
                    System.err.println("Warning: DSLD file " + extraDslds[i] + " doesn't exist.  Ignoring.");
                }
            }
        }
    }

    private void processCommandLine(String[] args) {
        boolean doHelp = false;
        String excludes = null;
        String includes = null;
        projectName = args[args.length - 1];

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("-h") || arg.equals("--help")) {
                doHelp = true;
                break;
            } else if (arg.equals("--assertions_only")) {
                assertionsOnly = true;
            } else if (arg.equals("--excludes")) {
                excludes = args[++i];
            } else if (arg.equals("--includes")) {
                includes = args[++i];
            } else if (arg.equals("--assertions_only")) {
                assertionsOnly = true;
            } else if (arg.equals("--extra_dslds")) {
                extraDslds = args[++i].split("\\|");
            }
        }
        
        inclusionFilters = convertToCharChar(includes);
        exclusionFilters = convertToCharChar(excludes);
        
        if (doHelp) {
            printUsage(false);
            Workbench.getInstance().close();
        }
    }
    
    /**
     * @param includes
     * @return
     */
    private char[][] convertToCharChar(String str) {
        if (str == null) {
            return null;
        }
        String[] splits = str.split("\\|");
        char[][] chars = new char[splits.length][];
        for (int i = 0; i < splits.length; i++) {
            chars[i] = ("/" + projectName + "/" + splits[i]).toCharArray();
        }
        return chars;
    }

    private void printUsage(boolean isInvalid) {
        if (isInvalid) {
            System.out.println("Invalid command line.");
        }
        
        System.out.println("Usage:");
        System.out.println("eclipse -application org.codehause.groovy.eclipse.staticCheck [--help] [-h] [--extra_dslds <FILES>] [--assertions_only] [--excludes <PATH>] [--includes <PATH>] <PROJECT_NAME>");
        System.out.println("where:");
        System.out.println("\t--help OR -h  Print this message and exit.");
        System.out.println("\t--extra_dslds  list of extra dsld files to be included in this check.  Use '|' as a file separator.");
        System.out.println("\t--assertions_only  Don't report unknown types.  Only look for type assertions");
        System.out.println("\t--excludes  Project-relative exclusion filters.");
        System.out.println("\t--includes  Project-relative inclusion filters.");
        System.out.println("<PROJECT_NAME>  Name of a project to type check.  Must be already in the workspace.");
        
        System.out.println("Ant style filters are allowed.  Eg, src/org/codehaus/groovy/**/*.groovy means all files with groovy extensions in the org.codehaus.groovy package or below will be ex/included   Filters can be concentenated using '|'.");
    }

    /**
     * 
     */
    private void removeExtraDslds() {
        if (extraDsldFiles != null) {
            for (IFile file : extraDsldFiles) {
                if (file != null && file.exists()) {
                    try {
                        System.out.println("\nRemoving " + file.getLocation().toFile().toURI());
                        file.delete(true, null);
                    } catch (CoreException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Creates the display used by the application.
     * 
     * @return the display used by the application
     */
    protected Display createDisplay() {
        return PlatformUI.createDisplay();
    }
}
