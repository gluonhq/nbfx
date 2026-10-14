package com.gluonhq.netbeans.nbfx.ui.context;

import com.gluonhq.netbeans.nbfx.api.editor.EditorContext;
import com.gluonhq.netbeans.nbfx.api.editor.EditorDocument;
import com.gluonhq.netbeans.nbfx.api.project.OpenProject;
import com.gluonhq.netbeans.nbfx.api.view.ViewProvider;
import com.gluonhq.netbeans.nbfx.api.view.ViewSelectionContext;

import java.util.concurrent.atomic.AtomicReference;

import org.openide.filesystems.FileObject;
import org.openide.util.ContextGlobalProvider;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 * Feeds the active nbfx component's lookup into the NetBeans global context
 * ({@link org.openide.util.Utilities#actionsGlobalContext()}).
 * <p>
 * This mirrors the Swing {@code GlobalActionContextImpl}: where that one proxies the activated
 * {@code TopComponent}'s lookup and re-resolves it when the activation changes, this one follows the
 * active nbfx view or editor document. The context is a stable {@link Lookups#proxy proxy} - never
 * rebuilt, so the {@code Lookup.Result}s consumers hold keep firing - whose delegate is the active
 * component's {@link Lookup}:
 * <ul>
 *   <li>a view tab contributes {@link ViewProvider#getLookup()}; a navigator thereby publishes its
 *       own selection, so two navigators never answer for each other;</li>
 *   <li>an editor tab contributes the {@link EditorDocument}'s lookup (the document and its file);</li>
 *   <li>a view that presents nothing of its own leaves the context on the last selection-bearing
 *       component (the shell's fallback), so activating Properties or the Output window does not blank
 *       the global selection.</li>
 * </ul>
 * <p>
 * {@code Utilities.actionsGlobalContext()} uses a single {@code ContextGlobalProvider}; nbfx registers
 * this one so the global context reflects the JavaFX frontend rather than the hidden Swing window. The
 * explicit {@code position} makes {@code Lookup.getDefault()} pick this one over the swing
 * {@code GlobalActionContextImpl}.
 */
@ServiceProvider(service = ContextGlobalProvider.class, position = 100)
public final class NbfxGlobalContextProvider implements ContextGlobalProvider {

    /** The context is a stable proxy; this is the lookup it currently delegates to. */
    private final AtomicReference<Lookup> active = new AtomicReference<>(Lookup.EMPTY);

    /**
     * The last active component that carried a selection. A follower view that exposes nothing of its
     * own leaves the context here instead of blanking it (the shell's fallback).
     */
    private final AtomicReference<Lookup> lastSelection = new AtomicReference<>(Lookup.EMPTY);

    @Override
    public Lookup createGlobalContext() {
        ViewSelectionContext views = Lookup.getDefault().lookup(ViewSelectionContext.class);
        EditorContext editors = Lookup.getDefault().lookup(EditorContext.class);
        // A stable proxy: it delegates to whatever the active component's lookup currently is, and
        // re-resolves (firing LookupEvents) whenever we publish a new one.
        Lookup proxy = Lookups.proxy(active::get);
        Runnable publish = () -> {
            ViewProvider view = views == null ? null : views.activeView().getValue();
            EditorDocument document = editors == null ? null : editors.activeDocumentProperty().getValue();
            active.set(computeActive(view, document));
            // Forces the proxy to re-check its delegate and fire to its listeners (SimpleProxyLookup
            // resolves lazily, exactly like Swing pokes its proxy after an activation change).
            proxy.lookup(Object.class);
        };
        if (views != null) {
            views.activeView().subscribe(view -> publish.run());
        }
        if (editors != null) {
            editors.activeDocumentProperty().subscribe(document -> publish.run());
        }
        publish.run();
        return proxy;
    }

    /**
     * The lookup the global context should delegate to right now: the active view's lookup when it
     * carries a selection of its own; the active editor document's when no view is active (an editor
     * tab is the active component, so it must win over a navigator's earlier selection); otherwise -
     * a follower view that exposes nothing is active - the last selection-bearing component, so the
     * previous file/editor is kept.
     */
    private Lookup computeActive(ViewProvider view, EditorDocument document) {
        Lookup candidate = view == null ? null : view.getLookup();
        if (view == null && document != null) {
            candidate = document.getLookup();
        }
        if (!hasSelection(candidate)) {
            candidate = lastSelection.get();
            if (!hasSelection(candidate) && document != null) {
                candidate = document.getLookup();
            }
        }
        if (hasSelection(candidate)) {
            lastSelection.set(candidate);
            return candidate;
        }
        return Lookup.EMPTY;
    }

    /**
     * Whether {@code lookup} carries an nbfx selection (a file, a document, a project or a view). A
     * follower view that exposes none of these leaves the context on the previous component.
     */
    private static boolean hasSelection(Lookup lookup) {
        return lookup != null
                && (lookup.lookup(FileObject.class) != null
                        || lookup.lookup(EditorDocument.class) != null
                        || lookup.lookup(OpenProject.class) != null
                        || lookup.lookup(ViewProvider.class) != null);
    }
}
