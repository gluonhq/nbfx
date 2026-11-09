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
package org.netbeans.modules.nbfx.platform.status;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.netbeans.api.nbfx.status.StatusMessageService;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default status message service: one current message and listeners. Products resolve it from the
 * Lookup and set the message; the platform itself has no built-in caller.
 *
 * @since 1.1
 */
@ServiceProvider(service = StatusMessageService.class)
public final class StatusMessage implements StatusMessageService {

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private volatile String message = "";

    @Override
    public void setMessage(String message) {
        String value = message == null ? "" : message;
        this.message = value;
        for (Consumer<String> listener : List.copyOf(listeners)) {
            listener.accept(value);
        }
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public void addListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public void removeListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }
}
