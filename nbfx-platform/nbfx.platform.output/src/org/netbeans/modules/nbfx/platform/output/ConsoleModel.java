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
package org.netbeans.modules.nbfx.platform.output;

import java.util.function.Consumer;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * The model behind a console: the accumulated text plus the hooks the view installs to render it.
 * Appends may come from a background thread, so they are marshalled to the JavaFX Application
 * Thread; the text is kept even before the view exists, and flushed to it when it attaches.
 */
final class ConsoleModel implements ConsolePanel {

    private final String name;
    private final StringBuilder text = new StringBuilder();
    /** How many times the console was cleared: tells the text of before a clear from the text after it. */
    private int generation;
    /** The generation and the length of the text the view received when it attached. */
    private int attachedGeneration;
    private int attachedLength;
    private Consumer<String> appender;
    private Runnable clearer;
    private Runnable shower;
    private final ObjectProperty<Runnable> rerun = new SimpleObjectProperty<>(this, "rerun");
    private final ObjectProperty<Runnable> stop = new SimpleObjectProperty<>(this, "stop");

    ConsoleModel(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public synchronized void append(String delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }
        int at = generation;
        int offset = text.length();
        text.append(delta);
        FxThread.run(() -> {
            Consumer<String> target = appenderFor(at, offset);
            if (target != null) {
                target.accept(delta);
            }
        });
    }

    @Override
    public synchronized void clear() {
        text.setLength(0);
        int at = ++generation;
        FxThread.run(() -> {
            Runnable target = clearerFor(at);
            if (target != null) {
                target.run();
            }
        });
    }

    @Override
    public void show() {
        FxThread.run(() -> {
            Runnable target;
            synchronized (this) {
                target = shower;
            }
            if (target != null) {
                target.run();
            }
        });
    }

    @Override
    public synchronized String text() {
        return text.toString();
    }

    @Override
    public void setRerun(Runnable action) {
        FxThread.run(() -> rerun.set(action));
    }

    @Override
    public void setStop(Runnable action) {
        FxThread.run(() -> stop.set(action));
    }

    /** How to run the activity again, or {@code null}. Read on the JavaFX Application Thread. */
    ObjectProperty<Runnable> rerunProperty() {
        return rerun;
    }

    /** How to stop the running activity, {@code null} when nothing runs. Read on the JavaFX Application Thread. */
    ObjectProperty<Runnable> stopProperty() {
        return stop;
    }

    /**
     * Installs the view hooks and flushes the text accumulated so far. Called on the FX thread.
     * Appends and clears made before this call but still on their way to the FX thread are part of
     * what is flushed here, and are not delivered a second time.
     */
    synchronized void attach(Consumer<String> appender, Runnable clearer, Runnable shower) {
        this.appender = appender;
        this.clearer = clearer;
        this.shower = shower;
        attachedGeneration = generation;
        attachedLength = text.length();
        appender.accept(text.toString());
    }

    /** Removes the view hooks: the console keeps accumulating, nothing shows it. */
    synchronized void detach() {
        appender = null;
        clearer = null;
        shower = null;
    }

    /** The appender, unless the text appended at {@code offset} of {@code at} was flushed by {@link #attach} or cleared since. */
    private synchronized Consumer<String> appenderFor(int at, int offset) {
        if (at != generation || (at == attachedGeneration && offset < attachedLength)) {
            return null;
        }
        return appender;
    }

    /** The clearer, unless the clear starting generation {@code at} came before {@link #attach}. */
    private synchronized Runnable clearerFor(int at) {
        return at <= attachedGeneration ? null : clearer;
    }
}
