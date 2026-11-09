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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.nbfx.view.ActiveComponent;
import org.netbeans.api.nbfx.view.ActiveComponentContext;
import org.openide.util.ContextGlobalProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 * Feeds the active nbfx component's lookup into the NetBeans global context
 * ({@link org.openide.util.Utilities#actionsGlobalContext()}).
 *
 * <p>This is the platform-level counterpart of the Swing {@code GlobalActionContextImpl}: the
 * context is a stable proxy whose delegate is the active editor's or view's lookup. It lives in the
 * platform shell so a product that runs the platform-only cluster (no IDE tier) still gets
 * context-sensitive commands. A view that exposes nothing of its own keeps the last
 * selection-bearing component, so a follower view does not blank the selection.</p>
 *
 * @since 1.0
 */
@ServiceProvider(service = ContextGlobalProvider.class, position = 100)
public final class NbfxGlobalContextProvider implements ContextGlobalProvider {

    private final AtomicReference<Lookup> active = new AtomicReference<>(Lookup.EMPTY);
    private final AtomicReference<Lookup> lastSelection = new AtomicReference<>(Lookup.EMPTY);

    @Override
    public Lookup createGlobalContext() {
        ActiveComponentContext components =
                Lookup.getDefault().lookup(ActiveComponentContext.class);
        Lookup proxy = Lookups.proxy(active::get);
        if (components != null) {
            components.activeComponent().subscribe(component -> publish(proxy, component));
        }
        publish(proxy, components == null ? null : components.activeComponent().getValue());
        return proxy;
    }

    private void publish(Lookup proxy, ActiveComponent component) {
        active.set(computeActive(component));
        proxy.lookup(Object.class);
    }

    private Lookup computeActive(ActiveComponent component) {
        if (component instanceof ActiveComponent.EditorComponent editor) {
            Lookup lookup = editor.getLookup();
            lastSelection.set(lookup);
            return lookup;
        }
        if (component instanceof ActiveComponent.ViewComponent view
                && hasSelection(view.getLookup())) {
            lastSelection.set(view.getLookup());
            return view.getLookup();
        }
        Lookup kept = lastSelection.get();
        return hasSelection(kept) ? kept : Lookup.EMPTY;
    }

    /**
     * Whether the lookup carries a selection. A view that returns an empty lookup is a follower and
     * keeps the previous selection; any non-empty lookup is the view's own selection.
     */
    private static boolean hasSelection(Lookup lookup) {
        return lookup != null && lookup.lookup(Object.class) != null;
    }
}
