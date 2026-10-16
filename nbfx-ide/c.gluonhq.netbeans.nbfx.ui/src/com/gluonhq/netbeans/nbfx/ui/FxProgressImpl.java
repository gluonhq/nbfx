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
package com.gluonhq.netbeans.nbfx.ui;

import com.gluonhq.netbeans.nbfx.api.progress.FxProgress;
import java.util.concurrent.atomic.AtomicReference;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The launcher's {@link FxProgress}: it is a thin adapter over the standard progress API, so the
 * nbfx-notified activity (a project load, a build) becomes an ordinary system task shown by the FX
 * progress UI in the status line and in the running-tasks popup. Only one activity uses the slot at
 * a time: a new {@link #start} renames the running one, and {@link #finish} ends it.
 *
 * @since 1.0
 */
@ServiceProvider(service = FxProgress.class)
public final class FxProgressImpl implements FxProgress {

    private final AtomicReference<ProgressHandle> handle = new AtomicReference<>();
    private final AtomicReference<Runnable> cancel = new AtomicReference<>();

    @Override
    public void start(String message, Runnable onCancel) {
        cancel.set(onCancel);
        ProgressHandle current = handle.get();
        if (current != null) {
            current.setDisplayName(message);
            return;
        }
        ProgressHandle created = ProgressHandle.createSystemHandle(message, this::doCancel);
        if (handle.compareAndSet(null, created)) {
            created.start();
        } else {
            created.finish();
        }
    }

    @Override
    public void finish() {
        cancel.set(null);
        ProgressHandle current = handle.getAndSet(null);
        if (current != null) {
            current.finish();
        }
    }

    private boolean doCancel() {
        Runnable action = cancel.get();
        if (action != null) {
            action.run();
            return true;
        }
        return false;
    }
}
