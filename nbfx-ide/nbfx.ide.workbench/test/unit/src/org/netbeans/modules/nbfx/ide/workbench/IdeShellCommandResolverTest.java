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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.input.KeyCombination;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.AbstractCommand;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.EditorDocument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * The IDE resolver owns the scoped commands it creates; a menu rebuild must not create a new
 * scoped command every time, and closing the chrome must dispose them.
 */
public class IdeShellCommandResolverTest {

    @Test
    public void cachesScopedCommandsAndDisposesThem() {
        TestCommand scoped = new TestCommand("edit.cut");
        TestRegistry registry = new TestRegistry();
        registry.scoped = scoped;
        IdeShellCommandResolver resolver = new IdeShellCommandResolver(registry,
                new SimpleObjectProperty<>(), new SimpleBooleanProperty(false));

        assertSame(scoped, resolver.resolve("edit.cut"));
        assertSame(scoped, resolver.resolve("edit.cut"));
        assertEquals(1, registry.scopedCalls);

        resolver.close();
        assertEquals(1, scoped.disposed);
    }

    @Test
    public void fallsBackToTheRegisteredCommand() {
        TestCommand global = new TestCommand("global");
        TestRegistry registry = new TestRegistry();
        registry.register(global);
        IdeShellCommandResolver resolver = new IdeShellCommandResolver(registry,
                new SimpleObjectProperty<>(), new SimpleBooleanProperty(false));

        assertSame(global, resolver.resolve("global"));
    }

    private static final class TestCommand extends AbstractCommand {

        private int disposed;

        TestCommand(String id) {
            super(id, id, (KeyCombination) null, false);
        }

        @Override
        public void run() {
        }

        @Override
        public void dispose() {
            disposed++;
        }
    }

    private static final class TestRegistry implements ActionRegistry {

        private final Map<String, Command> commands = new LinkedHashMap<>();
        private Command scoped;
        private int scopedCalls;

        @Override
        public void register(Command command) {
            commands.put(command.getId(), command);
        }

        @Override
        public Optional<Command> find(String id) {
            return Optional.ofNullable(commands.get(id));
        }

        @Override
        public Collection<Command> getCommands() {
            return commands.values();
        }

        @Override
        public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> activeDocument) {
            if (scoped != null && id.equals(scoped.getId())) {
                scopedCalls++;
                return Optional.of(scoped);
            }
            return Optional.empty();
        }
    }
}
