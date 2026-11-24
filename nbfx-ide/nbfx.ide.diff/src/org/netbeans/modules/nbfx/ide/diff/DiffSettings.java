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
package org.netbeans.modules.nbfx.ide.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import org.openide.util.NbPreferences;

/**
 * Keeps how the table of files of a diff is laid out - as a list or a tree, its columns and its
 * height - from
 * one diff tab to the next and across restarts, as the Git window keeps its own.
 */
final class DiffSettings {

    private static final String KEY_TREE = "files.tree";
    private static final String KEY_COLUMNS = "files.columns";
    private static final String KEY_SHARE = "files.share";

    private DiffSettings() {
    }

    /** Lays {@code browser} out as the last one was left, and keeps what the user changes in it. */
    static void attach(DiffBrowser browser) {
        Preferences preferences = NbPreferences.forModule(DiffSettings.class);
        browser.setTree(preferences.getBoolean(KEY_TREE, false));
        browser.restoreColumns(parse(preferences.get(KEY_COLUMNS, "")));
        // None kept yet: the table takes the height of its rows.
        browser.setTableShare(preferences.getDouble(KEY_SHARE, -1));
        browser.setOnLayoutChanged(() -> {
            preferences.putBoolean(KEY_TREE, browser.isTree());
            preferences.put(KEY_COLUMNS, format(browser.columns()));
            if (browser.tableShare() > 0) {
                preferences.putDouble(KEY_SHARE, browser.tableShare());
            }
        });
    }

    /** The columns as one line: {@code id=width:visible}, joined by commas. */
    static String format(List<DiffBrowser.ColumnState> columns) {
        return columns.stream()
                .map(column -> column.id() + "=" + column.width() + ":" + column.visible())
                .collect(Collectors.joining(","));
    }

    /** The columns of a line of {@link #format}; none when it cannot be read. */
    static List<DiffBrowser.ColumnState> parse(String line) {
        List<DiffBrowser.ColumnState> columns = new ArrayList<>();
        for (String entry : line.split(",")) {
            int equals = entry.indexOf('=');
            int colon = entry.indexOf(':', equals + 1);
            if (equals <= 0 || colon < 0) {
                continue;
            }
            try {
                columns.add(new DiffBrowser.ColumnState(entry.substring(0, equals),
                        Double.parseDouble(entry.substring(equals + 1, colon)),
                        Boolean.parseBoolean(entry.substring(colon + 1))));
            } catch (NumberFormatException corrupt) {
                return List.of();
            }
        }
        return columns;
    }
}
