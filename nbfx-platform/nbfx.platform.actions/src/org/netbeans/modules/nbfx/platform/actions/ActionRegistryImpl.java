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
package org.netbeans.modules.nbfx.platform.actions;

import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.CommandsProvider;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.layer.LayerEntry;
import org.netbeans.api.nbfx.layer.LayerInstances;
import org.netbeans.api.nbfx.layer.LayerPaths;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import javafx.beans.value.ObservableValue;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default {@link ActionRegistry}. On creation, it registers transitional {@link CommandsProvider}
 * commands and then loads canonical {@link Command} instances from {@code NbFx/Actions} through
 * {@link LayerInstances}. Layer registrations override provider commands with the same id.
 */
@ServiceProvider(service = ActionRegistry.class)
public class ActionRegistryImpl implements ActionRegistry {

    private static final Logger LOG = Logger.getLogger(ActionRegistryImpl.class.getName());

    private final Map<String, Command> commands = new LinkedHashMap<>();
    private final Set<String> layerCommandIds = new HashSet<>();
    private final List<CommandsProvider> providers;

    public ActionRegistryImpl() {
        this(Lookup.getDefault(), FileUtil.getConfigFile(LayerPaths.ACTIONS));
    }

    ActionRegistryImpl(Lookup lookup, FileObject actionFolder) {
        providers = List.copyOf(lookup.lookupAll(CommandsProvider.class));
        for (CommandsProvider provider : providers) {
            provider.createCommands().forEach(this::register);
        }
        for (LayerEntry<Command> entry : LayerInstances.load(actionFolder, Command.class)) {
            Command command = entry.instance();
            if (!entry.id().equals(command.getId())) {
                LOG.warning("Action layer id " + entry.id() + " does not match command id "
                        + command.getId() + "; ignoring " + entry.file().getPath());
                try {
                    command.dispose();
                } catch (RuntimeException ex) {
                    LOG.warning("Cannot dispose mismatched action " + entry.id() + ": " + ex.getMessage());
                }
                continue;
            }
            register(command);
            layerCommandIds.add(entry.id());
        }
        if (providers.isEmpty() && commands.isEmpty()) {
            LOG.warning("No CommandsProvider or NbFx/Actions registrations found");
        }
    }

    @Override
    public void register(Command command) {
        Objects.requireNonNull(command);
        commands.put(command.getId(), command);
    }

    @Override
    public Optional<Command> find(String id) {
        return Optional.ofNullable(commands.get(id));
    }

    @Override
    public Collection<Command> getCommands() {
        return Collections.unmodifiableCollection(commands.values());
    }

    @Override
    public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
        if (activeDocument == null) {
            return Optional.empty();
        }
        if (layerCommandIds.contains(id)) {
            return find(id).map(command -> new ScopedLayerCommand(command, activeDocument));
        }
        for (CommandsProvider provider : providers) {
            Optional<Command> scoped = provider.createScoped(id, activeDocument);
            if (scoped.isPresent()) {
                return scoped;
            }
        }
        return Optional.empty();
    }
}
