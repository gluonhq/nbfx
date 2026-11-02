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

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.netbeans.CLIHandler;
import org.openide.util.NbBundle;

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
        // Bundle.properties of this package, in the jar; NbBundle is on the startup classpath too.
        return NbBundle.getMessage(JdkCheck.class, "JdkCheck.problem",
                REQUIRED_JAVA, Runtime.version(), System.getProperty("java.home"));
    }

    /** Reports {@code message} on {@code err} and, with a display, in a dialog too. */
    static void report(String message, PrintStream err) {
        err.println(message);
        // netbeans64.exe has no console: show it too.
        if (!GraphicsEnvironment.isHeadless()) {
            dialog(message).setVisible(true);
        }
    }

    /**
     * The message as the platform's own start-up warning shows it (core.startup's {@code NbEvents}:
     * system look and feel, a dialog with the frame icons, the text in an HTML pane, a button row),
     * so that it looks like the dialog it replaces, but as an error, with Exit as the only choice.
     */
    static JDialog dialog(String message) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ReflectiveOperationException | RuntimeException | UnsupportedLookAndFeelException ex) {
            // the cross-platform look and feel will do
        }
        JTextPane text = new JTextPane();
        text.setContentType("text/html");
        text.setEditable(false);
        text.setOpaque(false);
        text.setText(html(message));
        JComponent body = text;
        if (text.getPreferredSize().width > 600 || text.getPreferredSize().height > 400) {
            text.setPreferredSize(new Dimension(600, 400));
            body = new JScrollPane(text);
        }
        JOptionPane pane = new JOptionPane(body, JOptionPane.ERROR_MESSAGE, JOptionPane.DEFAULT_OPTION, null);
        JButton exit = new JButton(NbBundle.getMessage(JdkCheck.class, "JdkCheck.exit"));
        exit.addActionListener(e -> pane.setValue(0));
        pane.setOptions(new Object[] {exit});
        pane.setInitialValue(exit);
        JDialog dialog = pane.createDialog(null, NbBundle.getMessage(JdkCheck.class, "JdkCheck.title"));
        dialog.setIconImages(frameIcons());
        dialog.setResizable(true);
        dialog.pack();
        return dialog;
    }

    /**
     * {@code message} as HTML: escaped, line breaks kept, and the indented lines (the commands) in
     * a monospaced font, indent included.
     */
    static String html(String message) {
        StringBuilder html = new StringBuilder();
        for (String line : message.split("\n", -1)) {
            if (html.length() > 0) {
                html.append("<br>");
            }
            int indent = 0;
            while (indent < line.length() && line.charAt(indent) == ' ') {
                indent++;
            }
            String escaped = line.substring(indent).replace("&", "&amp;").replace("<", "&lt;");
            if (indent > 0) {
                html.append("<code>").append("&nbsp;".repeat(indent)).append(escaped).append("</code>");
            } else {
                html.append(escaped);
            }
        }
        return html.toString();
    }

    /** The platform's frame icons; core.startup is on the startup classpath, like this class. */
    private static List<Image> frameIcons() {
        List<Image> icons = new ArrayList<>();
        ClassLoader loader = JdkCheck.class.getClassLoader();
        for (String name : new String[] {"frame.gif", "frame32.gif", "frame48.gif", "frame256.png"}) {
            URL url = loader.getResource("org/netbeans/core/startup/" + name);
            if (url != null) {
                icons.add(Toolkit.getDefaultToolkit().getImage(url));
            }
        }
        return icons;
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
