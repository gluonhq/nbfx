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
package org.netbeans.api.nbfx.content;

import java.util.Map;
import javafx.scene.Node;
import org.junit.Test;
import org.openide.util.Lookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The content lifecycle and factory seams carry identity, requirements and state. */
public class ContentContractTest {

    @Test
    public void factoryRoundTripsIdentityAndPayload() throws ContentCreationException {
        ContentId id = ContentId.of("nbfx:editor");
        ContentState state = new ContentState(id, "buffer-1", Map.of("caret", "12"));

        ContentFactory factory = new ContentFactory() {
            @Override
            public Content create(ContentState restored) {
                return new FakeContent(restored);
            }

            @Override
            public ContentState capture(Content content) {
                return content.saveState();
            }
        };

        Content content = factory.create(state);
        assertEquals(id, content.id());
        assertEquals("buffer-1", content.instanceKey());
        assertEquals(ContentKind.EDITOR, content.kind());
        assertEquals(Map.of("caret", "12"), content.saveState().payload());
        assertEquals(state, factory.capture(content));
    }

    @Test
    public void requirementsExposeNeutralDefaults() {
        assertTrue(ContentRequirements.DEFAULT.closable());
        assertTrue(ContentRequirements.DEFAULT.persistable());
        assertTrue(ContentRequirements.SESSION_ONLY.closable());
        assertFalse(ContentRequirements.SESSION_ONLY.persistable());
        assertFalse(ContentRequirements.FIXED.closable());
        assertFalse(ContentRequirements.FIXED.persistable());
    }

    /** A content whose node is not exercised by these contract tests. */
    private static final class FakeContent implements Content {

        private final ContentState state;

        FakeContent(ContentState state) {
            this.state = state;
        }

        @Override
        public ContentId id() {
            return state.id();
        }

        @Override
        public String instanceKey() {
            return state.instanceKey();
        }

        @Override
        public ContentKind kind() {
            return ContentKind.EDITOR;
        }

        @Override
        public String title() {
            return "Fake";
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
            return true;
        }

        @Override
        public void close() {
            // no resources in the fake
        }

        @Override
        public ContentState saveState() {
            return state;
        }

        @Override
        public void dispose() {
            // no resources in the fake
        }
    }
}