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

import java.util.List;

/**
 * Persists the per-project navigator tree state. The generic session restorer captures the expanded
 * nodes and selected node; the product supplies the storage for them.
 *
 * @since 1.0
 */
public interface ViewStateStore {

    /** A store that discards all writes. */
    ViewStateStore NONE = new ViewStateStore() {
        @Override
        public void setExpandedNodes(String projectPath, List<String> nodes) {
        }

        @Override
        public void setSelectedNode(String projectPath, String nodeId) {
        }
    };

    /** Persists the expanded-node identifiers of {@code projectPath}. */
    void setExpandedNodes(String projectPath, List<String> nodes);

    /** Persists the selected-node identifier of {@code projectPath}. */
    void setSelectedNode(String projectPath, String nodeId);
}
