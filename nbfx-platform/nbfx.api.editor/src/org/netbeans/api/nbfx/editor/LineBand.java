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
package org.netbeans.api.nbfx.editor;

import java.util.Objects;

/**
 * A band laid over whole lines of an editor, from edge to edge of its text area: how a view built
 * on editors - a diff - marks blocks of lines without touching their text, caret or selection. A
 * band of no lines is a rule drawn above {@code paragraph}, or below the last line when
 * {@code paragraph} is the number of lines: the place where lines are missing.
 *
 * @param paragraph the first line of the band, counted from zero
 * @param count the number of lines it covers; zero for a rule
 * @param styleClass the style class of the band, by which a stylesheet of the view colours it
 * @since 1.3
 */
public record LineBand(int paragraph, int count, String styleClass) {

    /** Checks that the band has a place and a style. */
    public LineBand {
        Objects.requireNonNull(styleClass, "styleClass");
        if (paragraph < 0 || count < 0) {
            throw new IllegalArgumentException("paragraph " + paragraph + ", count " + count);
        }
    }
}
