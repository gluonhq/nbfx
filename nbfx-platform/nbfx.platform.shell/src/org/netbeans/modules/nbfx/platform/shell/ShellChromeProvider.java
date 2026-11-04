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
package org.netbeans.modules.nbfx.platform.shell;

/**
 * Creates the chrome for one shell instance. The provider is a service; the returned chrome is
 * per-shell content and is disposed with the shell.
 *
 * @since 1.0
 */
public interface ShellChromeProvider {

    /**
     * Creates the chrome for {@code context}.
     *
     * @param context the shell context
     * @return the chrome, never {@code null}
     */
    ShellChrome create(ShellContext context);

    /**
     * Where this provider ranks when several are registered. The shell uses the highest priority;
     * the platform default uses {@code 0}, a product provider a higher value.
     *
     * @return the priority; higher wins
     */
    default int priority() {
        return 0;
    }
}
