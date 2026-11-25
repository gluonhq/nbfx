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
package org.netbeans.modules.nbfx.platform.content;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import javafx.geometry.Point2D;
import org.junit.Test;
import org.netbeans.api.nbfx.content.DropDecision;
import org.netbeans.api.nbfx.content.DropHandler;
import org.netbeans.api.nbfx.content.DropRequest;
import org.netbeans.api.nbfx.content.DropTarget;
import org.netbeans.api.nbfx.content.DropTargetKind;
import org.netbeans.api.nbfx.content.TransferKind;
import org.netbeans.api.nbfx.content.TransferMode;
import org.netbeans.api.nbfx.content.TransferSource;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;

import static org.junit.Assert.*;

public class DropRouterTest {

    @Test
    public void resolvesFirstAcceptedHandlerInTargetPriorityOrder() {
        TransferKind files = TransferKind.FILES;
        DropTarget content = new DropTarget(DropTargetKind.CONTENT, "editor");
        DropTarget area = new DropTarget(DropTargetKind.AREA, "main");
        RecordingHandler contentHandler = new RecordingHandler(content, Set.of(files),
                DropDecision.reject("nope"));
        RecordingHandler areaHandler = new RecordingHandler(area, Set.of(files),
                DropDecision.allow(TransferMode.COPY, null));
        TransferSource source = new TransferSource() {
            @Override
            public TransferKind kind() {
                return files;
            }

            @Override
            public Lookup data() {
                return Lookup.EMPTY;
            }
        };

        Optional<DropRouter.ResolvedDrop> resolved = DropRouter.resolve(source,
                List.of(content, area), new Point2D(1, 2), TransferMode.COPY,
                Lookups.fixed(), List.of(contentHandler, areaHandler));

        assertTrue(resolved.isPresent());
        assertSame(areaHandler, resolved.get().handler());
        assertEquals(area, resolved.get().request().target());
        assertTrue(resolved.get().decision().allowed());
        assertEquals(1, contentHandler.evaluations);
        assertEquals(1, areaHandler.evaluations);
    }

    @Test
    public void resolvesUriAndTextSources() {
        DropTarget target = new DropTarget(DropTargetKind.GROUP, "nbfx:main");
        RecordingHandler uriHandler = new RecordingHandler(target, Set.of(TransferKind.URIS),
                DropDecision.allow(TransferMode.COPY, null));
        RecordingHandler textHandler = new RecordingHandler(target, Set.of(TransferKind.TEXT),
                DropDecision.allow(TransferMode.COPY, null));
        TransferSource uriSource = new TransferSource() {
            @Override public TransferKind kind() { return TransferKind.URIS; }
            @Override public Lookup data() { return Lookups.fixed("https://example.test"); }
        };
        TransferSource textSource = new TransferSource() {
            @Override public TransferKind kind() { return TransferKind.TEXT; }
            @Override public Lookup data() { return Lookups.fixed("hello"); }
        };
        assertTrue(DropRouter.resolve(uriSource, List.of(target), Point2D.ZERO, TransferMode.COPY,
                Lookup.EMPTY, List.of(uriHandler, textHandler)).isPresent());
        assertTrue(DropRouter.resolve(textSource, List.of(target), Point2D.ZERO, TransferMode.COPY,
                Lookup.EMPTY, List.of(uriHandler, textHandler)).isPresent());
    }

    @Test
    public void resolvesNodeSource() {
        DropTarget target = new DropTarget(DropTargetKind.CONTENT, "nbfx:view.test");
        RecordingHandler handler = new RecordingHandler(target, Set.of(TransferKind.NODES),
                DropDecision.allow(TransferMode.MOVE, null));
        TransferSource source = new TransferSource() {
            @Override public TransferKind kind() { return TransferKind.NODES; }
            @Override public Lookup data() { return Lookups.fixed("node"); }
        };
        Optional<DropRouter.ResolvedDrop> resolved = DropRouter.resolve(source, List.of(target),
                Point2D.ZERO, TransferMode.COPY, Lookup.EMPTY, List.of(handler));
        assertTrue(resolved.isPresent());
        assertEquals(TransferMode.MOVE, resolved.get().decision().effect());
    }

    @Test
    public void ignoresHandlersWithWrongTargetOrSourceKind() {
        TransferKind files = TransferKind.FILES;
        TransferKind uris = TransferKind.URIS;
        DropTarget target = new DropTarget(DropTargetKind.CONTENT, "editor");
        RecordingHandler wrongKind = new RecordingHandler(target, Set.of(uris),
                DropDecision.allow(TransferMode.COPY, null));
        TransferSource source = new TransferSource() {
            @Override
            public TransferKind kind() {
                return files;
            }

            @Override
            public Lookup data() {
                return Lookup.EMPTY;
            }
        };

        Optional<DropRouter.ResolvedDrop> resolved = DropRouter.resolve(source,
                List.of(target), Point2D.ZERO, TransferMode.COPY, Lookup.EMPTY, List.of(wrongKind));

        assertFalse(resolved.isPresent());
        assertEquals(0, wrongKind.evaluations);
    }

    private static final class RecordingHandler implements DropHandler {
        private final DropTarget target;
        private final Set<TransferKind> kinds;
        private final DropDecision decision;
        int evaluations;

        RecordingHandler(DropTarget target, Set<TransferKind> kinds, DropDecision decision) {
            this.target = target;
            this.kinds = kinds;
            this.decision = decision;
        }

        @Override
        public DropTarget target() {
            return target;
        }

        @Override
        public Set<TransferKind> sourceKinds() {
            return kinds;
        }

        @Override
        public DropDecision evaluate(DropRequest request) {
            evaluations++;
            return decision;
        }

        @Override
        public void perform(DropRequest request, DropDecision accepted) {
        }
    }
}
