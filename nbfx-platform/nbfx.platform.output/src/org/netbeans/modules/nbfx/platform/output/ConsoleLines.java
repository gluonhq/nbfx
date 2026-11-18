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

import java.util.ArrayList;
import java.util.List;

/**
 * The lines of a console, as the Output view shows them: the appended text cut at its line breaks.
 * There is always a last line, empty after a line break, which the next append continues. Not
 * thread-safe: the view owns it on the JavaFX Application Thread.
 */
final class ConsoleLines {

    /**
     * What an append changed, in the terms of a text model's change event: where the text went in,
     * how many characters joined that line, how many lines came after it and how many characters
     * the last of them holds.
     */
    record Change(int line, int offset, int charsOnFirstLine, int linesAdded, int charsOnLastLine) {
    }

    private final List<StringBuilder> lines = new ArrayList<>();

    ConsoleLines() {
        lines.add(new StringBuilder());
    }

    /** The number of lines, at least one. */
    int size() {
        return lines.size();
    }

    /** The text of line {@code index}, without its line break. */
    String line(int index) {
        return lines.get(index).toString();
    }

    /** The length of the last line: with {@code size() - 1}, the end of the text. */
    int lastLineLength() {
        return lines.get(lines.size() - 1).length();
    }

    /**
     * Appends {@code text}, starting a line at each {@code \n}. Carriage returns are dropped, so
     * {@code \r\n} breaks a line once.
     *
     * @return what changed, or {@code null} when nothing was added
     */
    Change append(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        int line = lines.size() - 1;
        int offset = lastLineLength();
        StringBuilder current = lines.get(line);
        int charsOnFirstLine = 0;
        int linesAdded = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                current = new StringBuilder();
                lines.add(current);
                linesAdded++;
            } else if (c != '\r') {
                current.append(c);
                if (linesAdded == 0) {
                    charsOnFirstLine++;
                }
            }
        }
        if (charsOnFirstLine == 0 && linesAdded == 0) {
            return null;
        }
        return new Change(line, offset, charsOnFirstLine, linesAdded, linesAdded == 0 ? 0 : current.length());
    }

    /** Drops every line, leaving the one empty line of a new console. */
    void clear() {
        lines.clear();
        lines.add(new StringBuilder());
    }
}
