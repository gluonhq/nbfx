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
 * to compare. Each reason is told apart, as each is said differently to the user.
 * <p>
 * The content is that of the object git stores, read by its id: not what a filter or a
 * {@code working-tree-encoding} attribute makes of it on checkout. None of this says whether the
 * file was added or deleted: that is for who compares two of these to tell, from the side each is
 * on.
 *
 * @since 1.3
 */
sealed interface GitContent {

    /**
     * The content of a text file, decoded with the charset asked for.
     *
     * @param text the text
     * @param objectId the id of the blob it was read from: the same id is the same content
     * @param size the size of the blob, in bytes
     */
    record Text(String text, String objectId, long size) implements GitContent {
    }

    /**
     * The file is not there: not in the commit - or there is no commit yet - or not in the index.
     * On the older side of a comparison that is a file added since, on the newer one a file
     * deleted.
     */
    record Missing() implements GitContent {
    }

    /**
     * The file is there and is not text, as git tells it without attributes: it has a NUL among
     * its first bytes. A text in UTF-16 is binary by that rule, as it is for git.
     *
     * @param objectId the id of the blob
     * @param size the size of the blob, in bytes
     */
    record Binary(String objectId, long size) implements GitContent {
    }

    /**
     * The file is larger than the caller wants to read; nothing of it was read.
     *
     * @param size the size of the blob, in bytes
     * @param limit the most the caller asked for
     */
    record TooLarge(long size, long limit) implements GitContent {
    }

    /**
     * The file is in conflict: the index has the versions a merge could not join, and no content
     * of its own for it.
     */
    record Unmerged() implements GitContent {
    }

    /**
     * What is at the path is not a file with content: a folder ({@code tree}) or another
     * repository, a submodule ({@code commit}).
     *
     * @param type what git has there
     */
    record NotAFile(String type) implements GitContent {
    }

    /**
     * The bytes of the file are not text in the charset asked for; none were replaced to make
     * them one.
     *
     * @param charset the name of the charset
     */
    record Malformed(String charset) implements GitContent {
    }

    /** The content could not be read; {@code message} is what git said of it, or what failed. */
    record Failed(String message) implements GitContent {
    }
}
