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
package org.netbeans.api.nbfx.layer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;

/**
 * Reads the class-based {@code .instance} entries the nbfx annotation processors write, with their
 * attributes and layer order, without {@code DataObject} or {@code InstanceCookie}.
 * <p>
 * An entry must carry an {@code instanceClass} attribute naming a public class with a public
 * no-argument constructor. Entries without it (factory {@code instanceCreate} entries and
 * serialized {@code .ser} entries) are skipped; nbfx registrations use class entries only.
 * <p>
 * {@link #registrations} reads the entries without creating them; {@link #instantiate} creates one
 * on demand; {@link #load} is the eager convenience. The entries are returned in the layer's
 * {@code position} order.
 *
 * @since 1.4
 */
public final class LayerInstances {

    private static final String INSTANCE_CLASS = "instanceClass";

    private static final Logger LOG = Logger.getLogger(LayerInstances.class.getName());

    /** The files already reported as broken, so a repeated read does not repeat the warning. */
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private LayerInstances() {
    }

    /**
     * Reads the class-based entries of {@code type} registered under {@code path}, without creating
     * their instances.
     *
     * @param path the layer folder, for example {@link LayerPaths#PROPERTY_EDITORS}
     * @param type the type the entries must provide
     * @param <T>  the type
     * @return the registrations in layer order, never {@code null}
     * @since 1.5
     */
    public static <T> List<LayerRegistration<T>> registrations(String path, Class<T> type) {
        return registrations(FileUtil.getConfigFile(path), type);
    }

    /**
     * Reads the class-based entries of {@code type} from an already resolved layer folder, without
     * creating their instances.
     *
     * @param folder the layer folder, or {@code null}
     * @param type   the type the entries must provide
     * @param <T>    the type
     * @return the registrations in layer order, never {@code null}
     * @since 1.5
     */
    public static <T> List<LayerRegistration<T>> registrations(FileObject folder, Class<T> type) {
        Objects.requireNonNull(type, "type");
        if (folder == null) {
            return List.of();
        }
        List<LayerRegistration<T>> registrations = new ArrayList<>();
        for (FileObject file : FileUtil.getOrder(List.of(folder.getChildren()), false)) {
            if (!file.isData() || !file.hasExt("instance")) {
                continue;
            }
            LayerRegistration<T> registration = registration(file, type);
            if (registration != null) {
                registrations.add(registration);
            }
        }
        return List.copyOf(registrations);
    }

    /**
     * Creates the instance of one registration.
     *
     * @param registration the registration to create
     * @param <T>          the instance type
     * @return the instance, or {@code null} when it cannot be created
     * @since 1.5
     */
    public static <T> T instantiate(LayerRegistration<T> registration) {
        Objects.requireNonNull(registration, "registration");
        try {
            return registration.type().getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            warnOnce(registration.file(),
                    "Cannot create " + registration.type().getName(), ex);
            return null;
        }
    }

    /**
     * Loads the instances of {@code type} registered under {@code path}.
     *
     * @param path the layer folder, for example {@link LayerPaths#PROPERTY_EDITORS}
     * @param type the type the entries must provide
     * @param <T>  the type
     * @return the entries in layer order, never {@code null}
     */
    public static <T> List<LayerEntry<T>> load(String path, Class<T> type) {
        return load(FileUtil.getConfigFile(path), type);
    }

    /**
     * Loads the instances of {@code type} from an already resolved layer folder.
     *
     * @param folder the layer folder, or {@code null}
     * @param type   the type the entries must provide
     * @param <T>    the type
     * @return the entries in layer order, never {@code null}
     */
    public static <T> List<LayerEntry<T>> load(FileObject folder, Class<T> type) {
        List<LayerEntry<T>> entries = new ArrayList<>();
        for (LayerRegistration<T> registration : registrations(folder, type)) {
            T instance = instantiate(registration);
            if (instance != null) {
                entries.add(new LayerEntry<>(instance, registration.file()));
            }
        }
        return List.copyOf(entries);
    }

    private static <T> LayerRegistration<T> registration(FileObject file, Class<T> type) {
        Object attribute = file.getAttribute(INSTANCE_CLASS);
        String name = attribute instanceof Class<?> clazz ? clazz.getName()
                : attribute instanceof String text ? text : null;
        if (name == null) {
            LOG.log(Level.FINE, "No {0} on {1}; factory entries are not supported",
                    new Object[]{INSTANCE_CLASS, file.getPath()});
            return null;
        }
        try {
            Class<?> clazz = attribute instanceof Class<?> known
                    ? known : Class.forName(name, false, classLoader());
            if (!type.isAssignableFrom(clazz)) {
                LOG.log(Level.FINE, "{0} is not a {1}", new Object[]{name, type.getName()});
                return null;
            }
            return new LayerRegistration<>(file, clazz.asSubclass(type));
        } catch (ReflectiveOperationException | LinkageError | SecurityException ex) {
            warnOnce(file, "Cannot load " + name, ex);
            return null;
        }
    }

    private static void warnOnce(FileObject file, String message, Throwable ex) {
        Level level = WARNED.add(file.getPath()) ? Level.WARNING : Level.FINE;
        LOG.log(level, message + " at " + file.getPath(), ex);
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = Lookup.getDefault().lookup(ClassLoader.class);
        if (loader != null) {
            return loader;
        }
        loader = Thread.currentThread().getContextClassLoader();
        return loader != null ? loader : LayerInstances.class.getClassLoader();
    }
}
