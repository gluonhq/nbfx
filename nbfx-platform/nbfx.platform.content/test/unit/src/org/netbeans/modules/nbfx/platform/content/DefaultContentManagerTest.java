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
import javafx.scene.Node;
import org.junit.Test;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentKind;
import org.netbeans.api.nbfx.content.ContentManager;
import org.netbeans.api.nbfx.content.ContentRequirements;
import org.netbeans.api.nbfx.content.ContentState;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The manager owns the open set, the active content and the close/dispose lifecycle. */
public class DefaultContentManagerTest {

    @Test
    public void firstOpenBecomesActiveAndFindUsesIdentity() {
        ContentManager manager = new DefaultContentManager();
        FakeContent first = content("nbfx:editor", "buffer-1");
        FakeContent second = content("nbfx:output", "console-1");

        manager.open(first);
        manager.open(second);

        assertSame(first, manager.activeContent().getValue());
        assertEquals(List.of(first, second), manager.openContent());
        assertSame(first, manager.find(first.id()).orElseThrow());
    }

    @Test
    public void duplicateIdentityIsNotAddedTwice() {
        ContentManager manager = new DefaultContentManager();
        FakeContent first = content("nbfx:editor", "buffer-1");
        FakeContent duplicate = content("nbfx:editor", "buffer-2");

        manager.open(first);
        manager.open(duplicate);

        assertEquals(List.of(first), manager.openContent());
        assertSame(first, manager.find(first.id()).orElseThrow());
    }

    @Test
    public void closeDisposesAndAdvancesActive() {
        ContentManager manager = new DefaultContentManager();
        FakeContent first = content("nbfx:editor", "buffer-1");
        FakeContent second = content("nbfx:output", "console-1");
        manager.open(first);
        manager.open(second);

        manager.close(first);

        assertTrue(first.closed);
        assertTrue(first.disposed);
        assertFalse(manager.find(first.id()).isPresent());
        assertEquals(List.of(second), manager.openContent());
        assertSame(second, manager.activeContent().getValue());
    }

    @Test
    public void closeIsVetoedWhenContentCannotClose() {
        ContentManager manager = new DefaultContentManager();
        FakeContent content = content("nbfx:editor", "buffer-1");
        content.canClose = false;
        manager.open(content);

        manager.close(content);

        assertFalse(content.closed);
        assertFalse(content.disposed);
        assertEquals(List.of(content), manager.openContent());
    }

    @Test
    public void closingTheLastContentClearsTheActiveContent() {
        ContentManager manager = new DefaultContentManager();
        FakeContent content = content("nbfx:editor", "buffer-1");
        manager.open(content);

        manager.close(content);

        assertNull(manager.activeContent().getValue());
        assertTrue(manager.openContent().isEmpty());
    }

    private static FakeContent content(String id, String instanceKey) {
        return new FakeContent(ContentId.of(id), instanceKey);
    }

    private static final class FakeContent implements Content {

        private final ContentId id;
        private final String instanceKey;
        private boolean canClose = true;
        private boolean closed;
        private boolean disposed;

        FakeContent(ContentId id, String instanceKey) {
            this.id = id;
            this.instanceKey = instanceKey;
        }

        @Override
        public ContentId id() {
            return id;
        }

        @Override
        public String instanceKey() {
            return instanceKey;
        }

        @Override
        public ContentKind kind() {
            return ContentKind.VIEW;
        }

        @Override
        public String title() {
            return instanceKey;
        }

        @Override
        public Node node() {
            return null;
        }

        @Override
        public Lookup getLookup() {
            return Lookup.EMPTY;
        }

        @Override
        public ContentRequirements requirements() {
            return ContentRequirements.DEFAULT;
        }

        @Override
        public boolean canClose() {
            return canClose;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public ContentState saveState() {
            return ContentState.empty(id, instanceKey);
        }

        @Override
        public void dispose() {
            disposed = true;
        }
    }
}