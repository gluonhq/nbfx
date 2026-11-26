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
package org.netbeans.api.nbfx.shell;

/**
 * What happens when a window is closed by the user or through {@link DockWindow#close()}.
 *
 * @since 1.15
 */
public enum WindowClosePolicy {

    /** Hide the window and keep its content live and managed. */
    HIDE,

    /** Redock floating content into the main window; invalid for custom product windows. */
    REDOCK,

    /** Close the content using the shell's normal close lifecycle. */
    CLOSE_CONTENT
}
