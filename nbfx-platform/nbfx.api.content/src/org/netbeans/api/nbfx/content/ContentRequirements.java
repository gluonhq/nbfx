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
package org.netbeans.api.nbfx.content;

/**
 * What a piece of content asks of its host and its store.
 *
 * @param closable   whether the user may close the content; a non-closable content is only removed
 *                   when its owner disposes it
 * @param persistable whether the content's state should be captured and restored across sessions
 * @since 1.1
 */
public record ContentRequirements(boolean closable, boolean persistable) {

    /** The neutral default: closable and persisted. */
    public static final ContentRequirements DEFAULT = new ContentRequirements(true, true);

    /** Closable but not restored across sessions. */
    public static final ContentRequirements SESSION_ONLY = new ContentRequirements(true, false);

    /** Neither closable by the user nor persisted. */
    public static final ContentRequirements FIXED = new ContentRequirements(false, false);
}