/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.nbfx.java.navigation;

import org.netbeans.modules.nbfx.java.navigation.utils.DeleteActions;

import java.util.ArrayList;
import java.util.List;

import javafx.scene.control.TreeItem;

import org.netbeans.api.project.ProjectManager;
import org.openide.filesystems.FileObject;
import org.netbeans.api.java.queries.AccessibilityQuery;
import org.netbeans.api.java.queries.AccessibilityQuery.Accessibility;
import org.openide.util.NbBundle;

/**
 * The {@link NavigatorContextMenuFactory.Host} implementations of the two navigator views. They live
 * apart from the tree cells so the rules they encode — which node is a project root and which nodes
 * can be deleted — can be exercised without instantiating JavaFX controls.
 * <p>
 * Both trees hold a hidden root whose children are the open projects' top-level nodes, so a project
 * root is a node whose <em>grandparent</em> is missing.
 */
final class NavigatorHosts {

    private NavigatorHosts() {
    }

    /** Whether {@code item} is the top-level node of an open project. */
    static boolean isProjectRoot(TreeItem<?> item) {
        return item != null && item.getParent() != null && item.getParent().getParent() == null;
    }

    /** The hooks of the physical Files view, whose nodes are {@link FileObject}s. */
    static NavigatorContextMenuFactory.Host<FileObject> files() {
        return new NavigatorContextMenuFactory.Host<>() {
            @Override
            public FileObject fileOf(TreeItem<FileObject> item) {
                return item == null ? null : item.getValue();
            }

            @Override
            public boolean isRoot(TreeItem<FileObject> item) {
                return isProjectRoot(item);
            }

            @Override
            public boolean isDeletable(TreeItem<FileObject> item) {
                // A project's own folder is closed, never deleted, from the tree.
                return item != null && item.getValue() != null
                        && item.getParent() != null && !isProjectRoot(item);
            }

            @Override
            public boolean isProject(TreeItem<FileObject> item) {
                FileObject fo = item == null ? null : item.getValue();
                return fo != null && fo.isFolder() && !isProjectRoot(item)
                        && ProjectManager.getDefault().isProject2(fo) != null;
            }

            @Override
            public void deleteItems(List<TreeItem<FileObject>> items) {
                DeleteActions.deleteItems(items, TreeItem::getValue);
            }
        };
    }

    /** The hooks of the logical Project view, whose nodes are {@link ProjectEntry}s. */
    static NavigatorContextMenuFactory.Host<ProjectEntry> project() {
        return new NavigatorContextMenuFactory.Host<>() {
            @Override
            public FileObject fileOf(TreeItem<ProjectEntry> item) {
                ProjectEntry entry = item == null ? null : item.getValue();
                return entry == null ? null : entry.getFileObject();
            }

            @Override
            public boolean isRoot(TreeItem<ProjectEntry> item) {
                return isProjectRoot(item);
            }

            @Override
            public boolean isDeletable(TreeItem<ProjectEntry> item) {
                return item != null && !isProjectRoot(item) && DeleteActions.isDeletable(item.getValue());
            }

            @Override
            public boolean isRecursive(TreeItem<ProjectEntry> item) {
                // A package node is its own files, the subpackages are its siblings.
                ProjectEntry entry = item == null ? null : item.getValue();
                return entry == null || entry.getType() != ProjectEntry.Type.PACKAGE;
            }

            @Override
            public List<FileObject> listedFiles(TreeItem<ProjectEntry> item) {
                // The project files group stands for the files it lists, wherever they are below
                // the project folder, and not for the folder: the sources are not its own.
                ProjectEntry entry = item == null ? null : item.getValue();
                if (entry == null || entry.getType() != ProjectEntry.Type.FILES) {
                    return null;
                }
                List<FileObject> files = new ArrayList<>();
                for (TreeItem<ProjectEntry> child : item.getChildren()) {
                    if (child.getValue() != null && child.getValue().getFileObject() != null) {
                        files.add(child.getValue().getFileObject());
                    }
                }
                return files;
            }

            @Override
            public String describe(TreeItem<ProjectEntry> item) {
                ProjectEntry entry = item == null ? null : item.getValue();
                if (entry == null) {
                    return "";
                }
                if (entry.getType() != ProjectEntry.Type.PACKAGE || entry.getFileObject() == null) {
                    return entry.getName();
                }
                // As NetBeans names a package: by whether its module exports it.
                Accessibility accessibility = Accessibility.UNKNOWN;
                try {
                    accessibility = AccessibilityQuery.isPubliclyAccessible2(entry.getFileObject()).getAccessibility();
                } catch (RuntimeException ex) {
                    // Told as a plain package.
                }
                String key = switch (accessibility) {
                    case EXPORTED -> "Package.exported";
                    case PRIVATE -> "Package.private";
                    case UNKNOWN -> "Package.plain";
                };
                return NbBundle.getMessage(NavigatorHosts.class, key, entry.getName());
            }

            @Override
            public boolean isProject(TreeItem<ProjectEntry> item) {
                // A nested NAME node is a subproject (the top-level NAME node is the tree root).
                ProjectEntry entry = item == null ? null : item.getValue();
                return entry != null
                        && entry.getType() == ProjectEntry.Type.NAME
                        && !isProjectRoot(item);
            }

            @Override
            public void deleteItems(List<TreeItem<ProjectEntry>> items) {
                DeleteActions.deleteItems(items, it -> {
                    ProjectEntry entry = it == null ? null : it.getValue();
                    return entry == null ? null : entry.getFileObject();
                });
            }
        };
    }
}
