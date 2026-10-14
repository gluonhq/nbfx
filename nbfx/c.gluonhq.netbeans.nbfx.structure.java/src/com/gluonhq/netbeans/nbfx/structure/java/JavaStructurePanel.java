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
package com.gluonhq.netbeans.nbfx.structure.java;

import com.gluonhq.netbeans.nbfx.api.actions.FxActionContext;
import com.gluonhq.netbeans.nbfx.structure.FxStructurePanel;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import org.netbeans.api.java.source.ClasspathInfo;
import org.netbeans.api.java.source.JavaSource;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle;
import org.openide.util.RequestProcessor;
import org.openide.util.lookup.ServiceProvider;

/**
 * The Java members structure panel: the types, methods and fields of the Java file in the global
 * action context, parsed with the shared javac pipeline. This is the first {@link FxStructurePanel}
 * and proves the structure/context contract end to end.
 */
@ServiceProvider(service = FxStructurePanel.class)
public final class JavaStructurePanel implements FxStructurePanel {

    private static final RequestProcessor RP = new RequestProcessor("nbfx-structure-java", 1, true, true);

    private final TreeView<String> tree = new TreeView<>();
    private final List<Lookup.Result<?>> subscriptions = new ArrayList<>();
    private LookupListener listener;
    private FileObject shownFile;

    @Override
    public String getDisplayName() {
        return NbBundle.getMessage(JavaStructurePanel.class, "JavaStructurePanel.title");
    }

    @Override
    public String[] contentTypes() {
        return new String[] {"text/x-java"};
    }

    @Override
    public Node getComponent() {
        return tree;
    }

    @Override
    public void panelActivated(FxActionContext context) {
        if (subscriptions.isEmpty()) {
            listener = event -> refresh(context);
            // The global context now follows the active component (§22), so the file it carries is
            // the only thing that decides the members; a single FileObject result replaces the
            // provisional three-type subscription.
            Lookup.Result<?> result = context.lookupResult(FileObject.class);
            result.addLookupListener(listener);
            subscriptions.add(result);
        }
        refresh(context);
    }

    @Override
    public void panelDeactivated() {
        for (Lookup.Result<?> result : subscriptions) {
            result.removeLookupListener(listener);
        }
        subscriptions.clear();
        listener = null;
    }

    /** Re-reads the file in {@code context}, rebuilding the tree only when it changed. */
    private void refresh(FxActionContext context) {
        FileObject file = FxStructurePanel.selectedFile(context);
        if (Objects.equals(file, shownFile)) {
            return;
        }
        shownFile = file;
        if (file == null) {
            setRoot(null);
            return;
        }
        RP.post(() -> setRoot(build(file)));
    }

    private void setRoot(TreeItem<String> root) {
        if (Platform.isFxApplicationThread()) {
            tree.setRoot(root);
        } else {
            Platform.runLater(() -> tree.setRoot(root));
        }
    }

    /** Parses {@code file} and builds the members tree; empty when it cannot be parsed. */
    private static TreeItem<String> build(FileObject file) {
        TreeItem<String> root = new TreeItem<>(file.getName());
        root.setExpanded(true);
        try {
            JavaSource source = JavaSource.create(ClasspathInfo.create(file), file);
            source.runUserActionTask(controller -> {
                controller.toPhase(JavaSource.Phase.PARSED);
                CompilationUnitTree unit = controller.getCompilationUnit();
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type) {
                        root.getChildren().add(typeItem(type));
                    }
                }
            }, true);
        } catch (Exception ex) {
            // Leave the root empty when the file cannot be parsed.
        }
        return root;
    }

    private static TreeItem<String> typeItem(ClassTree type) {
        TreeItem<String> item = new TreeItem<>(type.getSimpleName().toString());
        item.setExpanded(true);
        for (Tree member : type.getMembers()) {
            if (member instanceof ClassTree nested) {
                item.getChildren().add(typeItem(nested));
            } else if (member instanceof MethodTree method) {
                item.getChildren().add(new TreeItem<>(method.getName() + "()"));
            } else if (member instanceof VariableTree field) {
                item.getChildren().add(new TreeItem<>(field.getName().toString()));
            }
        }
        return item;
    }
}
