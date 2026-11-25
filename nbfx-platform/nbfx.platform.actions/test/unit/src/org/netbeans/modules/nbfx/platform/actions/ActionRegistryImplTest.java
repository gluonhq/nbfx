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

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
import org.junit.Test;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.actions.CommandsProvider;
import org.netbeans.api.nbfx.actions.RunnableCommand;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Tests layer-backed action loading and context resolution. */
public class ActionRegistryImplTest {

    @Test
    public void loadsLayerCommandsWithoutProvidersAndResolvesContext() throws Exception {
        LayerCommand.constructed = 0;
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject folder = fileSystem.getRoot().createFolder("Actions");
        layer(folder, "layer", LayerCommand.class);

        ActionRegistryImpl registry = new ActionRegistryImpl(Lookup.EMPTY, folder);
        assertEquals(1, LayerCommand.constructed);
        assertEquals(1, registry.getCommands().size());
        Command command = registry.find("layer").orElseThrow();
        assertSame(command, registry.resolve("layer", Lookup.EMPTY).orElseThrow());

        layer(folder, "context", ContextCommand.class);
        ActionRegistryImpl withContext = new ActionRegistryImpl(Lookup.EMPTY, folder);
        Command scoped = withContext.resolve("context", Lookups.fixed("Scoped")).orElseThrow();
        assertNotSame(withContext.find("context").orElseThrow(), scoped);
        assertEquals("Scoped", scoped.getText());
    }

    @Test
    public void layerScopedCommandTracksInitiallyNullAndChangingDocuments() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject folder = fileSystem.getRoot().createFolder("Actions");
        layer(folder, "context", ContextCommand.class);
        ActionRegistryImpl registry = new ActionRegistryImpl(Lookup.EMPTY, folder);
        SimpleObjectProperty<EditorDocument> activeDocument = new SimpleObjectProperty<>();

        Command scoped = registry.createScoped("context", activeDocument).orElseThrow();
        assertTrue(scoped.isDisabled());
        activeDocument.set(document("First"));
        assertEquals("First", scoped.getText());
        assertFalse(scoped.isDisabled());
        activeDocument.set(document("Second"));
        assertEquals("Second", scoped.getText());
        activeDocument.set(null);
        assertTrue(scoped.isDisabled());
        scoped.dispose();
    }

    @Test
    public void layerOverridesProviderAndSkipsMismatchedOrWrongTypes() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject folder = fileSystem.getRoot().createFolder("Actions");
        RunnableCommand providerCommand = new RunnableCommand("layer", "Provider", null, () -> {});
        RunnableCommand providerScoped = new RunnableCommand("layer", "Provider scoped", null, () -> {});
        boolean[] scopedCalled = {false};
        CommandsProvider provider = new CommandsProvider() {
            @Override
            public List<Command> createCommands() {
                return List.of(providerCommand);
            }

            @Override
            public Optional<Command> createScoped(String id, ObservableValue<EditorDocument> document) {
                scopedCalled[0] = true;
                return Optional.of(providerScoped);
            }
        };
        layer(folder, "layer", LayerCommand.class);
        layer(folder, "mismatch", MismatchCommand.class);
        layer(folder, "wrong", String.class);

        ActionRegistryImpl registry = new ActionRegistryImpl(Lookups.fixed(provider), folder);
        assertNotSame(providerCommand, registry.find("layer").orElseThrow());
        assertFalse(registry.find("mismatch").isPresent());
        assertFalse(registry.find("wrong").isPresent());
        assertTrue(MismatchCommand.disposed);
        assertEquals(1, registry.getCommands().size());

        Command layerScoped = registry.createScoped("layer",
                new SimpleObjectProperty<>(document("First"))).orElseThrow();
        assertEquals("layer", layerScoped.getId());
        assertEquals("Layer", layerScoped.getText());
        assertNotSame(providerScoped, layerScoped);
        assertFalse(scopedCalled[0]);
    }

    @Test
    public void providerFallbackRemainsAvailable() {
        RunnableCommand command = new RunnableCommand("provider", "Provider", null, () -> {});
        CommandsProvider provider = () -> List.of(command);
        ActionRegistryImpl registry = new ActionRegistryImpl(Lookups.fixed(provider), null);
        assertSame(command, registry.find("provider").orElseThrow());
    }

    private static EditorDocument document(String text) {
        return (EditorDocument) Proxy.newProxyInstance(
                EditorDocument.class.getClassLoader(), new Class<?>[] {EditorDocument.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLookup" -> Lookups.fixed(proxy, text);
                    case "isModified", "canUndo", "canRedo", "hasSelection", "isEditable" -> false;
                    default -> null;
                });
    }

    private static void layer(FileObject folder, String id, Class<?> type) throws Exception {
        FileObject file = folder.createData(id + ".instance");
        file.setAttribute("instanceClass", type);
    }

    public static final class LayerCommand extends RunnableCommand {

        private static int constructed;

        public LayerCommand() {
            super("layer", "Layer", null, () -> {});
            constructed++;
        }
    }

    public static final class ContextCommand extends RunnableCommand {

        public ContextCommand() {
            super("context", "Context", null, () -> {});
        }

        @Override
        public Command forContext(Lookup context) {
            return new RunnableCommand("context", context.lookup(String.class), null, () -> {});
        }
    }

    public static final class MismatchCommand extends RunnableCommand {

        private static boolean disposed;

        public MismatchCommand() {
            super("actual", "Mismatch", null, () -> {});
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }
}
