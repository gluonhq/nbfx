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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import org.netbeans.api.nbfx.content.ContentId;
import org.netbeans.api.nbfx.content.ContentState;
import org.openide.util.NbPreferences;

/**
 * The per-content persistence owner: it stores only the opaque payload of each content, keyed by
 * {@link ContentId} and instance key. It never owns tab order, group membership or window
 * placement - that is the window state's business - and it never interprets a payload.
 * <p>
 * Each content gets one child node named by a stable hash of its id and instance key; the id,
 * instance key and a base64-encoded payload live inside it, so arbitrary characters in payload keys,
 * values or instance keys cannot break the preferences tree. Writing a content replaces its previous
 * payload wholesale; {@link #save(ContentState)} is idempotent.
 *
 * @since 1.1
 */
public final class ContentStateStore {

    /** The preferences path of this store. */
    static final String PREFERENCES_PATH = "org/netbeans/modules/nbfx/content"; // NOI18N

    private static final String KEY_ID = "id"; // NOI18N
    private static final String KEY_INSTANCE = "instance"; // NOI18N
    private static final String KEY_PAYLOAD = "payload"; // NOI18N

    private static final char ENTRY_SEPARATOR = '\n';
    private static final char PART_SEPARATOR = '=';

    private final Preferences prefs;

    /** A store using the default {@value #PREFERENCES_PATH} preferences node. */
    public ContentStateStore() {
        this(NbPreferences.root().node(PREFERENCES_PATH));
    }

    /**
     * A store over the given preferences node. Package-visible for tests.
     *
     * @param prefs the node to persist into
     */
    ContentStateStore(Preferences prefs) {
        this.prefs = Objects.requireNonNull(prefs, "prefs");
    }

    /**
     * Stores {@code state}, replacing any payload previously stored for its id and instance key.
     *
     * @param state the content state to store
     */
    public synchronized void save(ContentState state) {
        Objects.requireNonNull(state, "state");
        Preferences node = prefs.node(nodeName(state.id(), state.instanceKey()));
        node.put(KEY_ID, state.id().value());
        node.put(KEY_INSTANCE, state.instanceKey());
        node.put(KEY_PAYLOAD, encode(state.payload()));
    }

    /**
     * Loads the state stored for {@code id} and {@code instanceKey}.
     *
     * @param id          the content identity
     * @param instanceKey the instance key
     * @return the stored state, or empty when nothing is stored or the entry is unreadable
     */
    public synchronized Optional<ContentState> load(ContentId id, String instanceKey) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(instanceKey, "instanceKey");
        String name = nodeName(id, instanceKey);
        if (!exists(name)) {
            return Optional.empty();
        }
        return read(prefs.node(name));
    }

    /**
     * Loads every readable stored state.
     *
     * @return the states, never {@code null}
     */
    public synchronized List<ContentState> loadAll() {
        List<ContentState> states = new ArrayList<>();
        try {
            for (String name : prefs.childrenNames()) {
                read(prefs.node(name)).ifPresent(states::add);
            }
        } catch (BackingStoreException ex) {
            // Fall back to whatever was readable; a broken store is not fatal.
        }
        return List.copyOf(states);
    }

    /**
     * Removes the payload stored for {@code id} and {@code instanceKey}.
     *
     * @param id          the content identity
     * @param instanceKey the instance key
     */
    public synchronized void remove(ContentId id, String instanceKey) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(instanceKey, "instanceKey");
        try {
            prefs.node(nodeName(id, instanceKey)).removeNode();
        } catch (BackingStoreException ex) {
            // Best effort: a leftover entry is harmless.
        }
    }

    /** Drops every stored payload. */
    public synchronized void clear() {
        try {
            for (String name : prefs.childrenNames()) {
                prefs.node(name).removeNode();
            }
        } catch (BackingStoreException ex) {
            // Best effort.
        }
    }

    private boolean exists(String name) {
        try {
            return prefs.nodeExists(name);
        } catch (BackingStoreException ex) {
            return false;
        }
    }

    private static Optional<ContentState> read(Preferences node) {
        String idValue = node.get(KEY_ID, null);
        String instanceKey = node.get(KEY_INSTANCE, null);
        if (idValue == null || instanceKey == null || instanceKey.isBlank()) {
            return Optional.empty();
        }
        ContentId id;
        try {
            id = ContentId.of(idValue);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.of(new ContentState(id, instanceKey, decode(node.get(KEY_PAYLOAD, ""))));
    }

    /** The stable child-node name: SHA-256 of id and instance key, lowercase hex. */
    static String nodeName(ContentId id, String instanceKey) {
        String key = id.value() + '\u0000' + instanceKey;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder name = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                name.append(Character.forDigit((value >> 4) & 0xF, 16));
                name.append(Character.forDigit(value & 0xF, 16));
            }
            return name.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static String encode(Map<String, String> payload) {
        if (payload.isEmpty()) {
            return "";
        }
        StringBuilder encoded = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (encoded.length() > 0) {
                encoded.append(ENTRY_SEPARATOR);
            }
            encoded.append(encodePart(entry.getKey())).append(PART_SEPARATOR)
                    .append(encodePart(entry.getValue()));
        }
        return encoded.toString();
    }

    private static Map<String, String> decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return Map.of();
        }
        Map<String, String> payload = new LinkedHashMap<>();
        for (String entry : encoded.split(String.valueOf(ENTRY_SEPARATOR))) {
            if (entry.isEmpty()) {
                continue;
            }
            int separator = entry.indexOf(PART_SEPARATOR);
            if (separator < 0) {
                continue;
            }
            payload.put(decodePart(entry.substring(0, separator)),
                    decodePart(entry.substring(separator + 1)));
        }
        return Map.copyOf(payload);
    }

    private static String encodePart(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodePart(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }
}