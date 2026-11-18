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

import java.util.Locale;
import jfx.incubator.scene.control.richtext.StyleResolver;
import jfx.incubator.scene.control.richtext.TextPos;
import jfx.incubator.scene.control.richtext.model.RichParagraph;
import jfx.incubator.scene.control.richtext.model.StyleAttributeMap;
import jfx.incubator.scene.control.richtext.model.StyledTextModelViewOnlyBase;

/**
 * The read-only text model the Output view renders a console with: one paragraph per
 * {@linkplain ConsoleLines console line}, built when the rich text area asks for it, so only the
 * lines on screen cost anything. The text is monospaced, at the size the user picked (Larger /
 * Smaller Font), in the editor's text colour of the theme.
 * <p>
 * The model owning the paragraphs is what makes the rest of the Output window possible without
 * touching the control: coloured and linked segments, and folds - a fold hides lines here and
 * draws its handle in the area's left side decorator.
 */
final class ConsoleTextModel extends StyledTextModelViewOnlyBase {

    private final ConsoleLines lines = new ConsoleLines();
    private String textStyle = textStyle(OutputSettings.DEFAULT_FONT_SIZE);

    @Override
    public int size() {
        return lines.size();
    }

    @Override
    public String getPlainText(int index) {
        return lines.line(index);
    }

    @Override
    public RichParagraph getParagraph(int index) {
        // An empty line gets its segment too: the line is as tall as its font.
        return RichParagraph.builder()
                .addWithInlineStyle(lines.line(index), textStyle)
                .build();
    }

    @Override
    public StyleAttributeMap getStyleAttributeMap(StyleResolver resolver, TextPos position, boolean forInsert) {
        return StyleAttributeMap.EMPTY;
    }

    /** The position after the last character. */
    TextPos end() {
        return TextPos.ofLeading(lines.size() - 1, lines.lastLineLength());
    }

    /** Appends {@code text} at the end. */
    void append(String text) {
        ConsoleLines.Change change = lines.append(text);
        if (change != null) {
            TextPos at = TextPos.ofLeading(change.line(), change.offset());
            fireChangeEvent(at, at, change.charsOnFirstLine(), change.linesAdded(), change.charsOnLastLine());
        }
    }

    /** Removes all the text. */
    void clear() {
        TextPos end = end();
        lines.clear();
        fireChangeEvent(TextPos.ZERO, end, 0, 0, 0);
    }

    /** The whole text, its lines joined with {@code \n}. */
    String text() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                text.append('\n');
            }
            text.append(lines.line(i));
        }
        return text.toString();
    }

    /** Shows the text {@code size} pixels tall. */
    void setFontSize(double size) {
        String style = textStyle(size);
        if (!style.equals(textStyle)) {
            textStyle = style;
            fireStyleChangeEvent(TextPos.ZERO, end());
        }
    }

    /**
     * Builds the paragraphs again. The area keeps the colour each segment resolved, so a new theme
     * shows in the text only once its paragraphs are rebuilt.
     */
    void refreshStyles() {
        fireStyleChangeEvent(TextPos.ZERO, end());
    }

    private static String textStyle(double size) {
        // The colour is part of the inline style: the area resolves a styled segment to its own
        // inline style, fill included, which a style sheet rule on the text would not override.
        return String.format(Locale.ROOT,
                "-fx-font-family: \"Monospaced\"; -fx-font-size: %.1fpx; -fx-fill: -nbfx-editor-fg;", size);
    }
}
