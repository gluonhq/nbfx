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

import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Test;
import org.netbeans.modules.nbfx.project.ui.api.BuildOutput.Kind;

/**
 * Verifies the kinds given to Maven's output: the lines of the {@code mvn} process, and the log
 * records of the embedder, which logs information as {@code FINER} and warnings and errors alike
 * as {@code FINE}.
 */
public class MavenOutputTest {

    @Test
    public void processLinesFollowTheirLevelPrefix() {
        assertEquals(Kind.ERROR, MavenGoals.lineKind("[ERROR] COMPILATION ERROR : "));
        assertEquals(Kind.ERROR, MavenGoals.lineKind("[ERROR] BUILD SUCCESS is not what this line says"));
        assertEquals(Kind.WARNING, MavenGoals.lineKind("[WARNING] Using platform encoding (UTF-8 actually)"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind("[INFO] Building mavenproject5 1.0-SNAPSHOT"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind("[INFO] ------------------------------------------------------------------------"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind("Hello World!"));
        assertEquals(Kind.NORMAL, MavenGoals.lineKind(""));
    }

    @Test
    public void processLinesShowGoalsAndTheResult() {
        assertEquals(Kind.MUTED,
                MavenGoals.lineKind("[INFO] --- resources:3.3.1:resources (default-resources) @ mavenproject5 ---"));
        assertEquals(Kind.SUCCESS, MavenGoals.lineKind("[INFO] BUILD SUCCESS"));
        assertEquals(Kind.ERROR, MavenGoals.lineKind("[INFO] BUILD FAILURE"));
    }

    @Test
    public void embedderInformationShowsGoalsAndTheResult() {
        assertEquals(Kind.MUTED, MavenOutput.recordKind(
                record(Level.FINER, "info", "--- compiler:3.13.0:compile (default-compile) @ app ---")));
        assertEquals(Kind.SUCCESS, MavenOutput.recordKind(record(Level.FINER, "info", "BUILD SUCCESS")));
        assertEquals(Kind.ERROR, MavenOutput.recordKind(record(Level.FINER, "info", "BUILD FAILURE")));
        assertEquals(Kind.NORMAL, MavenOutput.recordKind(record(Level.FINER, "info", "Building app 1.0")));
    }

    @Test
    public void embedderWarningsAndErrorsAreToldApartByTheLoggingMethod() {
        assertEquals(Kind.WARNING, MavenOutput.recordKind(record(Level.FINE, "warn", "JAR will be empty")));
        assertEquals(Kind.ERROR, MavenOutput.recordKind(record(Level.FINE, "error", "BUILD SUCCESS it is not")));
        assertEquals(Kind.ERROR, MavenOutput.recordKind(record(Level.FINE, "fatalError", "fatal")));
    }

    /**
     * The embedder's way of logging, with no source method set: the record finds the method that
     * logged it by itself, as long as it is asked while it is being published.
     */
    @Test
    public void theLoggingMethodIsFoundWhilePublishing() {
        Logger logger = Logger.getLogger(MavenOutputTest.class.getName() + ".plexus");
        List<Kind> kinds = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                kinds.add(MavenOutput.recordKind(record));
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Level previous = logger.getLevel();
        logger.addHandler(handler);
        logger.setLevel(Level.FINER);
        try {
            PlexusLikeLogger plexus = new PlexusLikeLogger(logger);
            plexus.info("Building app 1.0");
            plexus.warn("JAR will be empty");
            plexus.error("COMPILATION ERROR");
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(previous);
        }

        assertEquals(List.of(Kind.NORMAL, Kind.WARNING, Kind.ERROR), kinds);
    }

    /** Logs as the embedder's Plexus logger does: every level through {@code Logger.log}, from a method named after it. */
    private static final class PlexusLikeLogger {

        private final Logger logger;

        PlexusLikeLogger(Logger logger) {
            this.logger = logger;
        }

        void info(String message) {
            logger.log(Level.FINER, message, (Throwable) null);
        }

        void warn(String message) {
            logger.log(Level.FINE, message, (Throwable) null);
        }

        void error(String message) {
            logger.log(Level.FINE, message, (Throwable) null);
        }
    }

    private static LogRecord record(Level level, String method, String message) {
        LogRecord record = new LogRecord(level, message);
        record.setSourceMethodName(method);
        return record;
    }
}
