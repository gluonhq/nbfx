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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.theme.ThemeTokenProvider;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.modules.ModuleInfo;
import org.openide.modules.Modules;
import org.openide.util.Lookup;

/**
 * Collects component CSS resources that define theme token defaults. Layer entries are resolved
 * through the annotated class's module class loader; {@link ThemeTokenProvider} services are
 * resolved through their own class.
 *
 * @since 1.0
 */
public final class ThemeTokenRegistry {

    private static final Logger LOG = Logger.getLogger(ThemeTokenRegistry.class.getName());

    private record Entry(String id, int order, URL url) {}

    private ThemeTokenRegistry() {
    }

    /** Ordered stylesheet URLs, lowest order first. */
    public static List<String> stylesheets() {
        List<Entry> entries = new ArrayList<>();
        readLayer(entries);
        readProviders(entries);
        entries.sort(Comparator.comparingInt(Entry::order).thenComparing(Entry::id));
        return entries.stream().map(entry -> entry.url().toExternalForm()).toList();
    }

    private static void readLayer(List<Entry> entries) {
        FileObject folder = FileUtil.getConfigFile(LayerPaths.THEME_TOKENS);
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
            entries.add(new Entry(child.getName(), order, url));
        }
    }

    private static void readProviders(List<Entry> entries) {
        for (ThemeTokenProvider provider : Lookup.getDefault().lookupAll(ThemeTokenProvider.class)) {
            for (String resource : provider.stylesheets()) {
                URL url = provider.getClass().getResource(resource);
                if (url == null) {
                    LOG.warning("Cannot resolve theme token stylesheet " + resource
                            + " from " + provider.getClass().getName());
                    continue;
                }
                entries.add(new Entry(provider.getClass().getName() + "/" + resource,
                        provider.order(), url));
            }
        }
    }

    private static Class<?> ownerClass(String owner) {
        for (ModuleInfo module : Lookup.getDefault().lookupAll(ModuleInfo.class)) {
            try {
                return Class.forName(owner, false, module.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ex) {
                // Try the next module.
            }
        }
        try {
            return Class.forName(owner);
        } catch (ClassNotFoundException | LinkageError ex) {
            LOG.log(Level.FINE, "Cannot find theme token owner " + owner, ex);
            return null;
        }
    }
}
