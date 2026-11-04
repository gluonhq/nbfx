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
package org.netbeans.modules.nbfx.platform.shell.api;

import java.util.Objects;
import org.netbeans.api.nbfx.actions.ActionContext;
import org.netbeans.api.nbfx.actions.ActionRegistry;
import org.netbeans.api.nbfx.actions.Command;
import org.openide.util.Lookup;

/**
 * The context a chrome or chrome contribution receives. It is deliberately narrow: the shell's
 * action registry, the generic action context and a lookup for other services. Domain objects such
 * as editor documents are resolved by the contribution that needs them through the lookup; the
 * shell API does not depend on them.
 *
 * @since 1.0
 */
public record ShellContext(Lookup lookup) {

    public ShellContext {
        Objects.requireNonNull(lookup, "lookup");
    }

    /** The action registry, or {@code null} when none is available. */
    public ActionRegistry actions() {
        return lookup.lookup(ActionRegistry.class);
    }

    /** The generic action context, or {@code null} when none is available. */
    public ActionContext actionContext() {
        return lookup.lookup(ActionContext.class);
    }

    /** The chrome profile for this context; defaults to {@link ShellChromeProfile#ALL}. */
    public ShellChromeProfile profile() {
        ShellChromeProfile profile = lookup.lookup(ShellChromeProfile.class);
        return profile == null ? ShellChromeProfile.ALL : profile;
    }

    /**
     * Resolves the command registered under {@code id}. A {@link ShellCommandResolver} in the
     * lookup takes precedence; otherwise the registry is consulted.
     *
     * @param id the command id
     * @return the command, or {@code null}
     */
    public Command command(String id) {
        ShellCommandResolver resolver = lookup.lookup(ShellCommandResolver.class);
        if (resolver != null) {
            Command resolved = resolver.resolve(id);
            if (resolved != null) {
                return resolved;
            }
        }
        ActionRegistry registry = actions();
        return registry == null ? null : registry.find(id).orElse(null);
    }

    /**
     * The icon for {@code command}, preferring the command's own icon and falling back to the
     * {@link ShellIconProvider} in the lookup.
     *
     * @param command the command
     * @param size    the requested edge size in pixels
     * @return a new icon node, or {@code null}
     */
    public javafx.scene.Node icon(Command command, double size) {
        return ShellIconProvider.icon(this, command, size);
    }
}
