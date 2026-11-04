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

import org.netbeans.modules.nbfx.platform.shell.api.ShellCommandResolver;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.modules.nbfx.ide.actions.IdeActionIds;
import org.netbeans.modules.nbfx.platform.shell.DispatchingCommand;
import org.openide.util.Lookup;

/**
 * Scopes the main window's commands to its own selected editor, independent of which window has
 * focus, and dispatches Cut/Copy/Paste/Undo/Redo to the project tree while it is focused.
 */
final class IdeShellCommandResolver implements ShellCommandResolver {

    private final ActionRegistry registry;
    private final ObservableValue<EditorDocument> mainScope;
    private final ObservableValue<Boolean> preferFile;
    private final List<Command> scopedSink;
    private final Map<String, Optional<Command>> scoped = new HashMap<>();

    IdeShellCommandResolver(ObservableValue<EditorDocument> mainScope, ObservableValue<Boolean> preferFile) {
        this(Lookup.getDefault().lookup(ActionRegistry.class), mainScope, preferFile, null);
    }

    IdeShellCommandResolver(ActionRegistry registry, ObservableValue<EditorDocument> mainScope,
            ObservableValue<Boolean> preferFile) {
        this(registry, mainScope, preferFile, null);
    }

    IdeShellCommandResolver(ActionRegistry registry, ObservableValue<EditorDocument> mainScope,
            ObservableValue<Boolean> preferFile, List<Command> scopedSink) {
        this.registry = registry;
        this.mainScope = mainScope;
        this.preferFile = preferFile;
        this.scopedSink = scopedSink;
    }

    @Override
    public Command resolve(String id) {
        return resolve(id, preferFile);
    }

    @Override
    public Command resolve(String id, ObservableValue<Boolean> preferFile) {
        if (registry == null) {
            return null;
        }
        boolean absent = !scoped.containsKey(id);
        Command scopedCommand = scoped.computeIfAbsent(id,
                key -> registry.createScoped(key, mainScope)).orElse(null);
        if (absent && scopedCommand != null && scopedSink != null) {
            scopedSink.add(scopedCommand);
        }
        Command editor = scopedCommand != null ? scopedCommand : registry.find(id).orElse(null);
        if (editor == null) {
            return null;
        }
        ObservableValue<Boolean> dispatch = preferFile != null ? preferFile : this.preferFile;
        if (dispatch != null) {
            Command file = fileCommandFor(id);
            if (file != null) {
                return new DispatchingCommand(editor, file, dispatch);
            }
        }
        return editor;
    }

    /** The registered file-scoped counterpart of {@code commandId}, or {@code null} if none applies. */
    private Command fileCommandFor(String commandId) {
        String fileId = switch (commandId) {
            case ActionIds.CUT -> IdeActionIds.FILE_CUT;
            case ActionIds.COPY -> IdeActionIds.FILE_COPY;
            case ActionIds.PASTE -> IdeActionIds.FILE_PASTE;
            case ActionIds.UNDO -> IdeActionIds.FILE_UNDO;
            case ActionIds.REDO -> IdeActionIds.FILE_REDO;
            case ActionIds.FIND -> IdeActionIds.FILE_FIND;
            default -> null;
        };
        return fileId == null || registry == null ? null : registry.find(fileId).orElse(null);
    }

    @Override
    public void close() {
        if (scopedSink == null) {
            scoped.values().stream().flatMap(Optional::stream).forEach(Command::dispose);
        }
        scoped.clear();
    }
}
