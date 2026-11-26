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
package org.netbeans.api.nbfx.diff;

/**
 * What two lines may differ in and still count as the same line. Each option leaves out its own
 * part of a line, so that several together leave out what each does. White space is what
 * {@link Character#isWhitespace(int)} says; a line of nothing else is all leading and trailing.
 *
 * @param ignoreCase whether letters that differ only in case are the same
 * @param ignoreInnerWhitespace whether white space between the first and the last character that is
 *        not white space is left out
 * @param ignoreLeadingAndTrailingWhitespace whether white space at the start and the end of a line
 *        is left out
 * @since 1.0
 */
public record DiffOptions(boolean ignoreCase, boolean ignoreInnerWhitespace,
        boolean ignoreLeadingAndTrailingWhitespace) {

    /** Lines are the same when they are equal, character by character. */
    public static final DiffOptions EXACT = new DiffOptions(false, false, false);
}
