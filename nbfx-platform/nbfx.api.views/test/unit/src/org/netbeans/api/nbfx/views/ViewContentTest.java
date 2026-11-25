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
package org.netbeans.api.nbfx.views;

import javafx.scene.Node;
import org.junit.Test;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentKind;
import org.netbeans.api.nbfx.content.ContentRequirements;
import org.netbeans.api.nbfx.view.ViewProvider;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** A view adapts to the content lifecycle without owning extra state. */
public class ViewContentTest {

    @Test
    public void adaptsViewMetadataAndLifecycle() {
        FakeView view = new FakeView("navigator", "Navigator");
        ViewContent content = new ViewContent(view);

        assertEquals(ContentId.of("nbfx:view.navigator"), content.id());
        assertEquals("navigator", content.instanceKey());
        assertEquals(ContentKind.VIEW, content.kind());
        assertEquals("Navigator", content.title());
        assertNull(content.node());
        assertSame(Lookup.EMPTY, content.getLookup());
        assertEquals(ContentRequirements.DEFAULT, content.requirements());
        assertTrue(content.canClose());
        assertSame(view, content.view());

        content.close();
        assertTrue(view.closed);

        assertEquals(ContentId.of("nbfx:view.navigator"), content.saveState().id());
        assertTrue(content.saveState().payload().isEmpty());

        content.dispose();
    }

    @Test
    public void sanitizesUnsafeViewIds() {
        ViewContent content = new ViewContent(new FakeView("a/b:c d", "Title"));

        assertEquals("a/b:c d", content.instanceKey());
        assertEquals(ContentId.of("nbfx:view.a-b-c-d"), content.id());
        assertEquals(ContentId.of("nbfx:view.a-b-c-d"), content.saveState().id());
    }

    @Test
    public void blankViewIdUsesAStableFallback() {
        ViewContent content = new ViewContent(new FakeView(" ", "Title"));

        assertEquals("view", content.instanceKey());
        assertEquals(ContentId.of("nbfx:view.view"), content.id());
    }

    private static final class FakeView implements ViewProvider {

        private final String id;
        private final String title;
        private boolean closed;

        FakeView(String id, String title) {
            this.id = id;
            this.title = title;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public Node getView() {
            return null;
        }

        @Override
        public void viewClosed() {
            closed = true;
        }
    }
}