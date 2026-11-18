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
package org.netbeans.modules.nbfx.java.build.ant;

import org.netbeans.modules.nbfx.project.ui.api.BuildOutput;
import org.netbeans.modules.nbfx.project.ui.api.BuildProgress;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/** Verifies that an Ant build runs in-process and streams its output. */
public class AntBuildTest {

    @Test
    public void runsAntInProcess() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-ant");
        Files.writeString(dir.resolve("build.xml"),
                "<project default='hello'>"
                + "<target name='hello'><echo message='HELLO_ANT'/></target>"
                + "</project>");

        StringBuilder out = new StringBuilder();
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String text) {
                out.append(text);
            }
        };
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
            }

            @Override
            public void finish() {
            }
        };

        new AntBuild(dir, new String[] {"hello"}, output, progress).start();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && out.indexOf("BUILD SUCCESSFUL") < 0) {
            Thread.sleep(50);
        }
        assertTrue(out.toString(), out.indexOf("HELLO_ANT") >= 0);
        assertTrue(out.toString(), out.indexOf("BUILD SUCCESSFUL") >= 0);
    }

    /** Target names are muted, an echo is plain unless it is logged as an error, and a failed build is an error. */
    @Test
    public void marksTheKindsOfItsOutput() throws Exception {
        Path dir = Files.createTempDirectory("nbfx-ant");
        Files.writeString(dir.resolve("build.xml"),
                "<project default='ok'>"
                + "<target name='ok'><echo message='PLAIN'/><echo level='error' message='ECHOED_ERROR'/></target>"
                + "<target name='broken'><fail message='BROKEN'/></target>"
                + "</project>");

        String passed = run(dir, "ok", "BUILD SUCCESSFUL");
        assertTrue(passed, passed.contains("MUTED[ok:]"));
        assertTrue(passed, passed.contains("NORMAL[PLAIN]"));
        assertTrue(passed, passed.contains("ERROR[ECHOED_ERROR]"));
        assertTrue(passed, passed.contains("SUCCESS[BUILD SUCCESSFUL]"));

        String failed = run(dir, "broken", "BUILD FAILED");
        assertTrue(failed, failed.contains("MUTED[broken:]"));
        assertTrue(failed, failed.contains("ERROR[BUILD FAILED: BROKEN]"));
    }

    /** Runs {@code target} and returns its output, each piece as {@code KIND[text]}, once {@code until} shows. */
    private static String run(Path dir, String target, String until) throws Exception {
        StringBuffer out = new StringBuffer();
        BuildOutput output = new BuildOutput() {
            @Override
            public void clear() {
            }

            @Override
            public void show() {
            }

            @Override
            public void append(String text) {
                append(text, Kind.NORMAL);
            }

            @Override
            public void append(String text, Kind kind) {
                out.append(kind).append('[').append(text.strip()).append(']');
            }
        };
        BuildProgress progress = new BuildProgress() {
            @Override
            public void start(String name, Runnable cancel) {
            }

            @Override
            public void finish() {
            }
        };
        new AntBuild(dir, new String[] {target}, output, progress).start();
        long deadline = System.currentTimeMillis() + 30_000;
        while (System.currentTimeMillis() < deadline && out.indexOf(until) < 0) {
            Thread.sleep(50);
        }
        return out.toString();
    }
}
