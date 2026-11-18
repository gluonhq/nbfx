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
package org.netbeans.modules.nbfx.java.maven.api;

import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;

/**
 * What Maven's output is, for the colour the Output window gives it, as NetBeans: the line
 * announcing a plugin goal muted, {@code BUILD SUCCESS} a success, {@code BUILD FAILURE}, errors
 * and warnings in their own colours. The same for a build run in-process, whose messages come as
 * log records, and for one run as a process, whose lines carry their level as a prefix.
 */
final class MavenOutput {

    /** A plugin goal as Maven announces it: {@code --- resources:3.3.1:resources (default-resources) @ app ---}. */
    private static final Pattern GOAL = Pattern.compile("--- .+ ---");

    private static final String ERROR_PREFIX = "[ERROR]";
    private static final String WARNING_PREFIX = "[WARNING]";
    private static final String INFO_PREFIX = "[INFO]";

    private MavenOutput() {
    }

    /** The kind of a line of the {@code mvn} process: its {@code [LEVEL]} prefix, then what it says. */
    static Kind lineKind(String line) {
        if (line.startsWith(ERROR_PREFIX)) {
            return Kind.ERROR;
        }
        if (line.startsWith(WARNING_PREFIX)) {
            return Kind.WARNING;
        }
        return messageKind(line.startsWith(INFO_PREFIX) ? line.substring(INFO_PREFIX.length()) : line);
    }

    /**
     * The kind of a message the embedder logged. Its logger turns Maven's warnings, errors and
     * fatal errors all into {@link Level#FINE} records, so the level alone cannot tell a warning
     * from an error: the method of that logger which logged the record can, and the record knows
     * it while it is being published.
     */
    static Kind recordKind(LogRecord record) {
        if (record.getLevel().intValue() >= Level.FINE.intValue()) {
            return "warn".equals(record.getSourceMethodName()) ? Kind.WARNING : Kind.ERROR;
        }
        return messageKind(record.getMessage());
    }

    /** The kind of what an informational message says. */
    private static Kind messageKind(String message) {
        String text = message.strip();
        if (text.startsWith("BUILD SUCCESS")) {
            return Kind.SUCCESS;
        }
        if (text.startsWith("BUILD FAILURE")) {
            return Kind.ERROR;
        }
        return GOAL.matcher(text).matches() ? Kind.MUTED : Kind.NORMAL;
    }
}
