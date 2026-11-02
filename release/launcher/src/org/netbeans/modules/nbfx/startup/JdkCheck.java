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
package org.netbeans.modules.nbfx.startup;

import java.awt.GraphicsEnvironment;
import java.io.PrintStream;
import java.io.PrintWriter;
import javax.swing.JOptionPane;
import org.netbeans.CLIHandler;

/**
 * Refuses to start on a JDK older than nbfx needs, at every start.
 *
 * <p>The launchers take the first JDK they find ({@code --jdkhome}, {@code netbeans_jdkhome},
 * {@code JDK_HOME}, {@code JAVA_HOME}, the registry, the path), and on one older than
 * {@value #REQUIRED_JAVA} every nbfx module fails its {@code OpenIDE-Module-Java-Dependencies}
 * check: the platform then shows a Swing "could not install some modules" dialog listing them all,
 * and sits there with no window. {@link NoImport} catches that on the first start of a userdir, but
 * the import runs only while {@code var/imported} is missing, so a userdir that once started on the
 * right JDK gets the module dialog on the next start with a wrong one.
 *
 * <p>This is a {@link CLIHandler} of the {@link #WHEN_BOOT} kind: the bootstrap
 * ({@code org.netbeans.MainImpl}) runs every such handler registered in {@code META-INF/services}
 * on the startup classpath before it loads {@code core.startup}, on every start, and exits with the
 * handler's status when it is not zero.
 * The jar ({@code nbfx-platform/core/nbfx-noimport.jar}, see build.xml:-dist-launcher) is compiled
 * for Java 17, as the bootstrap is, so that this class loads on the JDKs it rejects.
 */
public final class JdkCheck extends CLIHandler {

    /** The Java version every nbfx module requires ({@code OpenIDE-Module-Java-Dependencies}). */
    static final int REQUIRED_JAVA = 25;

    /** The exit status of a start refused for its JDK. */
    private static final int WRONG_JDK = 2;

    public JdkCheck() {
        // WHEN_BOOT: notified in the bootstrap's initialize(), before core.startup. WHEN_INIT
        // handlers run from finishInitialization(), once the module system is up: too late.
        super(WHEN_BOOT);
    }

    /** The problem with the running JDK, or {@code null} when it is recent enough. */
    static String problem() {
        if (Runtime.version().feature() >= REQUIRED_JAVA) {
            return null;
        }
        return "nbfx needs JDK " + REQUIRED_JAVA + " or later, but was started with Java "
                + Runtime.version() + " (" + System.getProperty("java.home") + ").\n\n"
                + "Start it with a JDK " + REQUIRED_JAVA + ", either on the command line:\n"
                + "    bin/netbeans --jdkhome <path to JDK " + REQUIRED_JAVA + ">\n"
                + "    bin\\netbeans64.exe --jdkhome <path to JDK " + REQUIRED_JAVA + ">\n"
                + "or for every start, by setting netbeans_jdkhome in etc/netbeans.conf.";
    }

    /** Reports {@code message} on {@code err} and, with a display, in a dialog too. */
    static void report(String message, PrintStream err) {
        err.println(message);
        // netbeans64.exe has no console: show it too.
        if (!GraphicsEnvironment.isHeadless()) {
            JOptionPane.showMessageDialog(null, message, "nbfx", JOptionPane.ERROR_MESSAGE);
        }
    }

    @Override
    protected int cli(Args args) {
        String problem = problem();
        if (problem == null) {
            return 0;
        }
        report(problem, System.err);
        return WRONG_JDK;
    }

    @Override
    protected void usage(PrintWriter w) {
    }
}
