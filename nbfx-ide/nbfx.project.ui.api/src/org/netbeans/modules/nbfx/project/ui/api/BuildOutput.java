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
package org.netbeans.modules.nbfx.project.ui.api;

/**
 * The output sink for an in-process build: the Fx console, abstracted so build executors do not
 * depend on the output module.
 *
 * @since 1.0
 */
public interface BuildOutput {

    /** Clears the output. */
    void clear();

    /** Brings the output on screen. */
    void show();

    /** Appends {@code text} as {@link Kind#NORMAL}. */
    void append(String text);

    /**
     * Appends {@code text} as {@code kind}, which is how the output decides its colour. The
     * default implementation appends it as plain text.
     *
     * @param text the text to append
     * @param kind what the text is
     * @since 1.1
     */
    default void append(String text, Kind kind) {
        append(text);
    }

    /**
     * What a piece of build output is; the output shows each kind in its own colour.
     *
     * @since 1.1
     */
    enum Kind {

        /** Ordinary output. */
        NORMAL,

        /** An error: a failed build, a message the tool logs as an error, the error stream of what it runs. */
        ERROR,

        /** A warning. */
        WARNING,

        /** A success: the line reporting a build that passed. */
        SUCCESS,

        /** Text of lesser weight than the output around it, such as the name of the target being run. */
        MUTED
    }
}
