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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * AppShell guard: platform/AppShell modules must not import IDE, project, Java, JavaFX-usage or
 * parsing-engine types. Those are contributions to the shell, not part of it.
 */
public final class AppShellGuard {

    private static final List<String> FORBIDDEN = List.of(
            "org.netbeans.modules.nbfx.project.",
            "org.netbeans.api.nbfx.project.",
            "org.netbeans.modules.nbfx.ide.",
            "org.netbeans.modules.nbfx.java.",
            "org.netbeans.modules.nbfx.javafx.",
            "org.netbeans.modules.parsing.");

    private AppShellGuard() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length == 0 ? Paths.get(System.getProperty("user.dir")) : Paths.get(args[0]);
        List<String> violations = new ArrayList<>();
        Path platform = root.resolve("nbfx-platform");
        if (!Files.isDirectory(platform)) {
            System.out.println("[appshell] OK (no nbfx-platform directory)");
            return;
        }
        try (Stream<Path> files = Files.walk(platform)) {
            for (Path file : (Iterable<Path>) files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("/src/"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String forbidden : FORBIDDEN) {
                    if (text.contains("import " + forbidden)) {
                        violations.add(root.relativize(file) + ": imports " + forbidden + "*");
                    }
                }
            }
        }
        if (violations.isEmpty()) {
            System.out.println("[appshell] OK: platform modules contain no IDE/project/Java imports");
            return;
        }
        for (String violation : violations) {
            System.out.println("[appshell] " + violation);
        }
        System.exit(1);
    }
}
