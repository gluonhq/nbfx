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
package org.netbeans.modules.nbfx.platform.workspace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Empty divider brackets are resolved from registered area weights; explicit dividers win.
 */
public class DockTreesTest {

    @Test
    public void derivesMissingDividersFromAreaWeights() {
        DockTree tree = DockTrees.parse("H[](P:NAVIGATOR,P:MAIN)");

        DockTree resolved = DockTrees.resolveDividers(tree,
                role -> "NAVIGATOR".equals(role) ? 0.3 : 1.0);

        assertEquals("H[0.3000](P:NAVIGATOR,P:MAIN)", DockTrees.format(resolved));
    }

    @Test
    public void keepsExplicitDividers() {
        DockTree tree = DockTrees.parse("H[0.4000](P:NAVIGATOR,P:MAIN)");

        DockTree resolved = DockTrees.resolveDividers(tree, role -> 0.3);

        assertEquals("H[0.4000](P:NAVIGATOR,P:MAIN)", DockTrees.format(resolved));
    }

    @Test
    public void flexibleChildrenShareTheRemainder() {
        DockTree tree = DockTrees.parse("H[](P:NAVIGATOR,P:MAIN,P:OUTPUT)");

        DockTree resolved = DockTrees.resolveDividers(tree,
                role -> switch (role) {
                    case "NAVIGATOR", "OUTPUT" -> 0.3;
                    default -> 1.0;
                });

        assertEquals("H[0.3000,0.7000](P:NAVIGATOR,P:MAIN,P:OUTPUT)", DockTrees.format(resolved));
    }

    @Test
    public void equalSharesWithoutAreaWeights() {
        DockTree tree = DockTrees.parse("H[](P:LEFT,P:RIGHT)");

        DockTree resolved = DockTrees.resolveDividers(tree, _ -> null);

        assertEquals("H[0.5000](P:LEFT,P:RIGHT)", DockTrees.format(resolved));
    }
}
