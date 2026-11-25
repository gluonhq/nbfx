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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.value.ObservableValue;
import org.netbeans.api.nbfx.content.Content;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentManager;

/**
 * The per-application content manager: keeps the open content by identity, owns close and dispose,
 * and publishes the active content. One manager belongs to one shell instance.
 * <p>
 * The mutation methods run on the JavaFX Application Thread in a shell; the class is synchronized so
 * a headless test may drive it from any thread. A second content with an identity that is already
 * open is ignored, so closing one instance never loses the other.
 *
 * @since 1.0
 */
public final class DefaultContentManager implements ContentManager {

    private final Map<ContentId, Content> open = new LinkedHashMap<>();
    private final ReadOnlyObjectWrapper<Content> active = new ReadOnlyObjectWrapper<>();

    @Override
    public synchronized void open(Content content) {
        Objects.requireNonNull(content, "content");
        if (open.containsKey(content.id())) {
            return;
        }
        open.put(content.id(), content);
        if (active.get() == null) {
            active.set(content);
        }
    }

    @Override
    public synchronized void close(Content content) {
        if (content == null || open.get(content.id()) != content) {
            return;
        }
        if (!content.canClose()) {
            return;
        }
        open.remove(content.id());
        if (active.get() == content) {
            active.set(open.isEmpty() ? null : open.values().iterator().next());
        }
        try {
            content.close();
        } finally {
            content.dispose();
        }
    }

    @Override
    public synchronized Optional<Content> find(ContentId id) {
        return Optional.ofNullable(open.get(id));
    }

    @Override
    public synchronized List<Content> openContent() {
        return List.copyOf(open.values());
    }

    @Override
    public ObservableValue<Content> activeContent() {
        return active.getReadOnlyProperty();
    }
}