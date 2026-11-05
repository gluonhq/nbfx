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
package org.netbeans.api.nbfx.views;

/**
 * The behavioural kind of a dock pane. Editor panes host editor documents; view panes host tool
 * views. A product may register additional permanent view panes, but the editor pane remains the
 * single {@link #EDITOR} pane supplied by the content manager.
 *
 * @since 1.0
 */
public enum PaneKind {

    /** An editor pane. */
    EDITOR,

    /** A view/tool pane. */
    VIEW
}
