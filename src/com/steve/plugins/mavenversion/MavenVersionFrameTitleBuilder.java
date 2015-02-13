package com.steve.plugins.mavenversion;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.FrameTitleBuilder;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.containsIgnoreCase;
import static com.intellij.openapi.util.text.StringUtil.indexOfIgnoreCase;

public class MavenVersionFrameTitleBuilder extends FrameTitleBuilder {
    private static FrameTitleBuilder defaultBuilder;

    public static void setDefaultBuilder(FrameTitleBuilder defaultBuilder) {
        MavenVersionFrameTitleBuilder.defaultBuilder = defaultBuilder;
    }

    private Set<String> pomFilesListeningTo = new HashSet<String>();

    // listen for creation of new projects, or new pom.xml files
    public MavenVersionFrameTitleBuilder() {
        VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
            @Override
            public void fileCreated(@NotNull VirtualFileEvent event) {
                if ("pom.xml".equals(event.getFileName())) {
                    try {
                        watchThisProject(getProjectForFile(event.getFile()));
                    } catch (Exception e) {
                        // ignore exception when new project is first checked out from VCS
                    }
                }
            }
        });
    }

    // watch the root pom.xml for changes, if it exists
    private VirtualFile watchThisProject(Project project) {
        VirtualFile pomFile = project.getBaseDir().findChild("pom.xml");
        if (pomFile != null) {
            registerFileChangedListener(pomFile.getCanonicalPath());
        }
        return pomFile;
    }

    // this fires once when a project is opened, use this hook to watch the project's pom for changes
    @Override
    public String getProjectTitle(@NotNull Project project) {
        String mavenVersion = null;
        try {
            VirtualFile pomFile = watchThisProject(project);
            mavenVersion = determineMavenVersion(pomFile);
        } catch (Exception e) {
            // ignore...
        }
        if (mavenVersion != null) {
            // decorate the default builder
            return defaultBuilder.getProjectTitle(project).replace(" [", " " + mavenVersion + " - [");
        } else {
            return defaultBuilder.getProjectTitle(project);
        }
    }

    // no change
    @Override
    public String getFileTitle(@NotNull Project project, @NotNull VirtualFile virtualFile) {
        return defaultBuilder.getFileTitle(project, virtualFile);
    }

    private void registerFileChangedListener(final String pomFileCanonicalPath) {
        if (!pomFilesListeningTo.contains(pomFileCanonicalPath)) {
            pomFilesListeningTo.add(pomFileCanonicalPath);
            VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
                @Override
                public void contentsChanged(@NotNull VirtualFileEvent event) {
                    if (pomFileCanonicalPath.equals(event.getFile().getCanonicalPath())) {
                        determineMavenVersion(event.getFile());
                        updateFrameTitle(getProjectForFile(event.getFile()));
                    }
                }
            });
        }
    }

    private String determineMavenVersion(VirtualFile pomFile) {
        String mavenVersion = null;
        try {
            String pomAsString = new String(pomFile.contentsToByteArray());
            if (containsIgnoreCase(pomAsString, "<parent>") && containsIgnoreCase(pomAsString, "</parent>")) {
                pomAsString = pomAsString.substring(0, indexOfIgnoreCase(pomAsString, "<parent>", 0)) + pomAsString.substring(indexOfIgnoreCase(pomAsString, "</parent>", 0));
            }
            if (containsIgnoreCase(pomAsString, "<version>") && containsIgnoreCase(pomAsString, "</version>")) {
                mavenVersion = pomAsString.substring(indexOfIgnoreCase(pomAsString, "<version>", 0) + "<version>".length(), indexOfIgnoreCase(pomAsString, "</version>", 0)).trim();
            }
            if ("".equals(mavenVersion)) {
                mavenVersion = null;
            }
        } catch (Exception e) {
            // ignore...
        }
        return mavenVersion;
    }

    private void updateFrameTitle(Project project) {
        if (project != null) {
            ((IdeFrameImpl) WindowManager.getInstance().getIdeFrame(project)).setTitle(getProjectTitle(project));
        }
    }

    private Project getProjectForFile(VirtualFile pomFile) {
        // look through all open projects and see if this file is contained in it
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            try {
                if (ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(pomFile) != null) {
                    return project;
                }
            } catch (Exception e) {
                // ignore... directory index might not be initialized yet
            }
        }
        return null;
    }
}
