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
package org.netbeans.modules.nbfx.ide.vcs;

/**
 * What git has of a file at some point - the last commit, the index: its text, or why there is none
 * to compare. The three reasons are told apart, as each is said differently to the user: a file not
 * there is a new file, a binary one has no lines, and a failure is no content at all.
 *
 * @since 1.3
 */
sealed interface GitContent {

    /** The content of a text file, decoded with the charset asked for. */
    record Text(String text) implements GitContent {
    }

    /** The file is not there: not in the commit, or not in the index. */
    record Missing() implements GitContent {
    }

    /** The file is there and is not text: as git tells, it has a NUL among its first bytes. */
    record Binary() implements GitContent {
    }

    /** The content could not be read; {@code message} is what git said of it. */
    record Failed(String message) implements GitContent {
    }
}
