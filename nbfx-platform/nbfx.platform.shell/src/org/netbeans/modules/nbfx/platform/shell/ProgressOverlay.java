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
package org.netbeans.modules.nbfx.platform.shell;

import org.netbeans.api.nbfx.progress.Progress;
import java.util.concurrent.atomic.AtomicBoolean;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.util.lookup.ServiceProvider;

/**
 * The launcher's {@link Progress}: a thin adapter over the standard progress API, so every
 * nbfx-notified activity (a project load, a build) becomes an ordinary task shown by the FX
 * progress UI in the status line and in the running-tasks popup. Each {@link #start} creates an
 * independent task with its own cancel action; concurrent activities no longer share a slot.
 *
 * @since 1.0
 */
@ServiceProvider(service = Progress.class)
public final class ProgressOverlay implements Progress {

    @Override
    public Handle start(String message, Runnable onCancel) {
        ProgressHandle handle = ProgressHandle.createSystemHandle(message,
                onCancel == null ? null : () -> {
                    onCancel.run();
                    return true;
                });
        handle.start();
        return new HandleImpl(handle);
    }

    /** One real progress handle; {@link #finish()} is idempotent. */
    private static final class HandleImpl implements Handle {

        private static final int WORK_UNITS = 100;

        private final ProgressHandle handle;
        private final AtomicBoolean finished = new AtomicBoolean();
        private boolean determinate;
        private int lastProgress = -1;

        HandleImpl(ProgressHandle handle) {
            this.handle = handle;
        }

        @Override
        public synchronized void setMessage(String message) {
            if (!finished.get()) {
                handle.setDisplayName(message);
            }
        }

        @Override
        public synchronized void setProgress(double fraction) {
            if (finished.get()) {
                return;
            }
            if (fraction < 0) {
                if (determinate) {
                    handle.switchToIndeterminate();
                    determinate = false;
                    lastProgress = -1;
                }
                return;
            }
            if (!determinate) {
                handle.switchToDeterminate(WORK_UNITS);
                determinate = true;
            }
            int done = (int) Math.round(Math.max(0, Math.min(1, fraction)) * WORK_UNITS);
            // NetBeans progress work units are monotonic; a lower value is ignored. Call with a
            // negative fraction first to restart the bar from zero.
            if (done > lastProgress) {
                handle.progress(done);
                lastProgress = done;
            }
        }

        @Override
        public synchronized void finish() {
            if (finished.compareAndSet(false, true)) {
                handle.finish();
            }
        }
    }
}
