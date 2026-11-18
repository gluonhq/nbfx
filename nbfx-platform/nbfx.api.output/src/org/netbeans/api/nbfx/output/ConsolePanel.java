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
package org.netbeans.api.nbfx.output;

/**
 * A named output console: the text of a build, a run, a test or any other streaming activity, and
 * what the Output window can do about that activity - run it again, stop it. Every method is safe
 * to call from any thread.
 *
 * @since 1.0
 */
public interface ConsolePanel {

    /** The console's name, shown on its tab. */
    String getName();

    /** Appends {@code text} to the console. */
    void append(String text);

    /** Clears the console. */
    void clear();

    /** Brings the Output window on screen with this console in front. */
    void show();

    /**
     * The text accumulated so far.
     *
     * @return the console text, never {@code null}
     */
    String text();

    /**
     * Tells the Output window how to run again what fills this console: its <em>Re-run</em> action
     * calls {@code rerun} on the user interface thread, and is disabled without one or while a
     * {@linkplain #startRun run} is going on. The console keeps {@code rerun} until it is replaced
     * or the console is closed, so it should hold what names the activity - a folder, a command -
     * and not the objects of a project, which it would keep alive.
     *
     * @param rerun starts the activity again, or {@code null} when it cannot be
     */
    void setRerun(Runnable rerun);

    /**
     * Tells the Output window that an activity started filling this console and how to stop it:
     * its <em>Stop</em> action calls {@code stop} on the user interface thread until the returned
     * run is {@linkplain ConsoleRun#finish() finished}. A run starting while another is going on
     * takes its place; the earlier one finishing later changes nothing.
     *
     * @param stop stops the activity; {@code null} for an activity that cannot be stopped
     * @return the run, to finish when the activity ends
     */
    ConsoleRun startRun(Runnable stop);
}
