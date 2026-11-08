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
package org.netbeans.modules.nbfx.platform.theme;

import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.theme.ThemeTokenProvider;
import org.openide.filesystems.FileAttributeEvent;
import org.openide.filesystems.FileChangeAdapter;
import org.openide.filesystems.FileChangeListener;
import org.openide.filesystems.FileEvent;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileRenameEvent;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Collects component CSS resources that define theme token defaults. Layer entries are resolved
 * through the NetBeans system class loader; {@link ThemeTokenProvider} services are resolved
 * through their own class. The ordered list is cached until the {@code NbFx/ThemeTokens} layer
 * changes.
 *
 * @since 1.0
 */
public final class ThemeTokenRegistry {

    private static final Logger LOG = Logger.getLogger(ThemeTokenRegistry.class.getName());
    private static final Object LOCK = new Object();

    private record Entry(String id, int order, URL url) {}

    private static volatile List<String> cache;
    private static FileChangeListener listener;
    private static FileObject listenedFolder;

    private ThemeTokenRegistry() {
    }

    /** Ordered stylesheet URLs, lowest order first. */
    public static List<String> stylesheets() {
        List<String> cached = cache;
        if (cached != null) {
            return cached;
        }
        synchronized (LOCK) {
            if (cache == null) {
                cache = load();
            }
            return cache;
        }
    }

    private static List<String> load() {
        FileObject folder = FileUtil.getConfigFile(LayerPaths.THEME_TOKENS);
        listen(folder);
        Map<String, Entry> entries = new LinkedHashMap<>();
        URL defaults = NbfxTheme.class.getResource("impl/defaults.css");
        if (defaults != null) {
            entries.put(defaults.toExternalForm(),
                    new Entry("platform-defaults", Integer.MIN_VALUE, defaults));
        }
        readLayer(folder, entries);
        readProviders(entries);
        List<Entry> ordered = new ArrayList<>(entries.values());
        ordered.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::id));
        return ordered.stream().map(entry -> entry.url().toExternalForm()).toList();
    }

    private static void readLayer(FileObject folder, Map<String, Entry> entries) {
        if (folder == null) {
            return;
        }
        for (FileObject child : folder.getChildren()) {
            String owner = child.getAttribute("owner") instanceof String value ? value : "";
            String resource = child.getAttribute("resource") instanceof String value ? value : "";
            if (owner.isBlank() || resource.isBlank()) {
                LOG.warning("Ignoring incomplete theme token registration " + child.getName());
                continue;
            }
            Class<?> ownerClass = ownerClass(owner);
            URL url = ownerClass == null ? null : ownerClass.getResource(resource);
            if (url == null) {
                LOG.warning("Cannot resolve theme token stylesheet " + resource + " from " + owner);
                continue;
            }
            int order = child.getAttribute("order") instanceof Integer value ? value : 0;
            put(entries, new Entry(child.getName(), order, url));
        }
    }

    private static void readProviders(Map<String, Entry> entries) {
        for (ThemeTokenProvider provider : Lookup.getDefault().lookupAll(ThemeTokenProvider.class)) {
            try {
                readProvider(provider, entries);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Theme token provider "
                        + provider.getClass().getName() + " failed", ex);
            }
        }
    }

    private static void readProvider(ThemeTokenProvider provider, Map<String, Entry> entries) {
        List<String> resources = provider.stylesheets();
        if (resources == null) {
            LOG.warning("Theme token provider " + provider.getClass().getName()
                    + " returned no stylesheet list");
            return;
        }
        for (String resource : resources) {
            if (resource == null || resource.isBlank()) {
                LOG.warning("Ignoring a blank theme token stylesheet from "
                        + provider.getClass().getName());
                continue;
            }
            URL url = provider.getClass().getResource(resource);
            if (url == null) {
                LOG.warning("Cannot resolve theme token stylesheet " + resource
                        + " from " + provider.getClass().getName());
                continue;
            }
            put(entries, new Entry(provider.getClass().getName() + "/" + resource,
                    provider.order(), url));
        }
    }

    /** Keeps the entry with the lowest order when the same stylesheet is registered twice. */
    private static void put(Map<String, Entry> entries, Entry entry) {
        entries.merge(entry.url().toExternalForm(), entry,
                (existing, added) -> existing.order() <= added.order() ? existing : added);
    }

    private static Class<?> ownerClass(String owner) {
        ClassLoader loader = Lookup.getDefault().lookup(ClassLoader.class);
        if (loader == null) {
            loader = Thread.currentThread().getContextClassLoader();
        }
        if (loader == null) {
            loader = ThemeTokenRegistry.class.getClassLoader();
        }
        try {
            return Class.forName(owner, false, loader);
        } catch (ClassNotFoundException | LinkageError ex) {
            LOG.log(Level.FINE, "Cannot find theme token owner " + owner, ex);
            return null;
        }
    }

    private static void listen(FileObject folder) {
        FileObject target = folder != null ? folder : FileUtil.getConfigFile("NbFx");
        if (target == null || target.equals(listenedFolder)) {
            return;
        }
        if (listener == null) {
            listener = new FileChangeAdapter() {
                @Override
                public void fileAttributeChanged(FileAttributeEvent event) {
                    invalidate();
                }

                @Override
                public void fileChanged(FileEvent event) {
                    invalidate();
                }

                @Override
                public void fileDataCreated(FileEvent event) {
                    invalidate();
                }

                @Override
                public void fileDeleted(FileEvent event) {
                    invalidate();
                }

                @Override
                public void fileFolderCreated(FileEvent event) {
                    invalidate();
                }

                @Override
                public void fileRenamed(FileRenameEvent event) {
                    invalidate();
                }
            };
        }
        if (listenedFolder != null) {
            listenedFolder.removeFileChangeListener(listener);
        }
        target.addFileChangeListener(listener);
        listenedFolder = target;
    }

    private static void invalidate() {
        synchronized (LOCK) {
            cache = null;
        }
    }
}
