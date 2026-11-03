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

import javafx.scene.Node;

/**
 * The platform's default chrome. The first increment only establishes the contract; the menu bar,
 * tool bars and status composition are added in the following chrome phases. The class owns the
 * concrete controls internally so they never become part of the product-facing {@link ShellChrome}
 * contract.
 *
 * @since 1.0
 */
public final class DefaultShellChrome implements ShellChrome {

    private final ShellContext context;

    /** Creates the default chrome for {@code context}. */
    public DefaultShellChrome(ShellContext context) {
        this.context = context;
    }

    /** The context this chrome was created for. */
    ShellContext context() {
        return context;
    }

    @Override
    public Node top() {
        return null;
    }

    @Override
    public Node bottom() {
        return null;
    }
}
