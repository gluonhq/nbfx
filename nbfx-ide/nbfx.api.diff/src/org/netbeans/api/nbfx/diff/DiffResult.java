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
package org.netbeans.api.nbfx.diff;

import java.util.List;

/**
 * The blocks of lines in which two texts differ, in the order of the lines; between two blocks,
 * and before the first and after the last, the texts have the same lines.
 *
 * @param differences the blocks, none overlapping another, from the top of the texts down
 * @since 1.0
 */
public record DiffResult(List<Difference> differences) {

    /**
     * Takes a copy of the blocks, and checks that they are in order and that none starts before
     * the one above it ends, on either side.
     *
     * @throws IllegalArgumentException when they are not
     */
    public DiffResult {
        differences = List.copyOf(differences);
        Difference above = null;
        for (Difference difference : differences) {
            if (above != null && (difference.leftStart() < above.leftEnd()
                    || difference.rightStart() < above.rightEnd())) {
                throw new IllegalArgumentException(difference + " starts before " + above + " ends");
            }
            above = difference;
        }
    }

    /**
     * Whether the comparison found no difference: the two texts have the same lines, as the
     * {@link DiffOptions} it was made with tell lines to be the same. With options that ignore
     * case or white space this is not that the texts are equal, which a caller that needs to know
     * asks of the texts.
     */
    public boolean isIdentical() {
        return differences.isEmpty();
    }
}
