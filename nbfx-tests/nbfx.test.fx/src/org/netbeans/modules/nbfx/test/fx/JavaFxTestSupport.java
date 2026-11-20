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
package org.netbeans.modules.nbfx.test.fx;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.stage.Window;

/**
 * Starts the headless JavaFX toolkit once per test JVM and runs tasks on the JavaFX thread.
 * <p>
 * Test-only support for the nbfx-qa cluster. The module is not in the product cluster config and is
 * not shipped.
 */
public final class JavaFxTestSupport {

    private static final AtomicBoolean FX_STARTED = new AtomicBoolean();

    private JavaFxTestSupport() {
    }

    /**
     * Runs {@code task} on the JavaFX thread and returns its result.
     *
     * @param <T>  the result type
     * @param task the task
     * @return the task's result
     * @throws InterruptedException when the test thread is interrupted
     */
    public static <T> T onFx(Supplier<T> task) throws InterruptedException {
        ensureFxStarted();
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.get());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            throw new AssertionError("timed out waiting for the JavaFX thread");
        }
        Throwable failure = error.get();
        if (failure instanceof AssertionError assertion) {
            throw assertion;
        }
        if (failure != null) {
            throw new AssertionError(failure);
        }
        return result.get();
    }

    /**
     * Runs {@code task} on the JavaFX thread.
     *
     * @param task the task
     * @throws InterruptedException when the test thread is interrupted
     */
    public static void onFx(Runnable task) throws InterruptedException {
        onFx(() -> {
            task.run();
            return null;
        });
    }

    /**
     * Hides every window that is still showing. Call it after a test that leaves a stage open: a
     * window that is showing when the test JVM exits may still be repainted while the toolkit
     * shuts down, and the renderer then rejects the job with a logged exception.
     *
     * @throws InterruptedException when the test thread is interrupted
     */
    public static void hideWindows() throws InterruptedException {
        onFx(() -> List.copyOf(Window.getWindows()).forEach(Window::hide));
    }

    private static void ensureFxStarted() throws InterruptedException {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
            // Tests show and hide stages: hiding the last one must not shut the toolkit down, or
            // whatever a later test posts to the FX thread is dropped and the test times out.
            Platform.setImplicitExit(false);
        }
    }
}
