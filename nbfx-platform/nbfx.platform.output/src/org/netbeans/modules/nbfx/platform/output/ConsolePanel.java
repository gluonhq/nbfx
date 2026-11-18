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

/**
 * A named JavaFX output console: the text of a build, a run, a test or any other streaming activity.
 * Appending is safe from any thread; the text is shown on the JavaFX Application Thread.
 *
 * @since 1.0
 */
public interface ConsolePanel {

    /** The console's name, shown on its tab. */
    String getName();

    /** Appends {@code text} to the console. Safe to call from any thread. */
    void append(String text);

    /** Clears the console. Safe to call from any thread. */
    void clear();

    /** Brings the console's tab to the front, if it is showing. */
    void show();

    /**
     * The text accumulated so far. Safe to call from any thread.
     *
     * @return the console text, never {@code null}
     * @since 1.0
     */
    String text();

    /**
     * Tells the Output view how to run again what fills this console: its <em>Re-run</em> button
     * and menu item call {@code rerun} on the JavaFX Application Thread, and are disabled without
     * one or while the console can be {@linkplain #setStop stopped}. Safe to call from any thread.
     *
     * @param rerun starts the activity again, or {@code null} when it cannot be
     * @since 1.1
     */
    default void setRerun(Runnable rerun) {
    }

    /**
     * Tells the Output view how to stop the activity filling this console: its <em>Stop</em> button
     * and menu item call {@code stop} on the JavaFX Application Thread. The activity sets it when it
     * starts and resets it to {@code null} when it ends, which is how the view knows whether
     * something is running. Safe to call from any thread.
     *
     * @param stop stops the running activity, or {@code null} once nothing is running
     * @since 1.1
     */
    default void setStop(Runnable stop) {
    }
}
