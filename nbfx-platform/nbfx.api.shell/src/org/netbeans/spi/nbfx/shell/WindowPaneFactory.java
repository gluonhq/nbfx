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
package org.netbeans.spi.nbfx.shell;

import org.netbeans.api.nbfx.shell.WindowPaneHost;

/**
 * Creates the shell's host for a declared {@link WindowPane}. A provider obtains the host from its
 * {@link WindowPaneContext} instead of constructing the shell's concrete pane type, which stays
 * internal.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface WindowPaneFactory {

    /**
     * Creates the host for {@code pane}.
     *
     * @param pane    the declared pane
     * @param context the narrow shell context
     * @return the host, never {@code null}
     */
    WindowPaneHost create(WindowPane pane, WindowPaneContext context);
}
