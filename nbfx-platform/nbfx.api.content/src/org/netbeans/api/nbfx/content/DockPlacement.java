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
 * Where a transfer puts its content. The vocabulary is neutral: docking and drop handling both use
 * it, and a rejection always names a placement so the UI can explain where the drop would have gone.
 *
 * @since 1.3
 */
public enum DockPlacement {

    /** Add to the target group, after its current content. */
    INTO_GROUP,
    /** Place before the target content. */
    BEFORE,
    /** Place after the target content. */
    AFTER,
    /** Place at the target group's tab strip. */
    TAB_STRIP,
    /** Split the target and place to its left. */
    SPLIT_LEFT,
    /** Split the target and place to its right. */
    SPLIT_RIGHT,
    /** Split the target and place above it. */
    SPLIT_TOP,
    /** Split the target and place below it. */
    SPLIT_BOTTOM,
    /** Dock along the left edge of the area. */
    EDGE_LEFT,
    /** Dock along the right edge of the area. */
    EDGE_RIGHT,
    /** Dock along the top edge of the area. */
    EDGE_TOP,
    /** Dock along the bottom edge of the area. */
    EDGE_BOTTOM,
    /** Float the content into its own redockable window. */
    FLOAT,
    /** Open the content in a new window. */
    NEW_WINDOW
}