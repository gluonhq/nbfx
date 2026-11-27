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

/**
 * One block of lines that differs between two texts, the left and the right one. Lines are counted
 * from zero. A side that has no lines in the block - the left of an addition, the right of a
 * deletion - has a count of zero and a start that tells where the lines of the other side would go:
 * before the line of that index, or at the end when it is the number of lines.
 *
 * @param kind whether lines were added, deleted or changed, going from left to right
 * @param leftStart the first line of the block in the left text
 * @param leftCount the number of lines of the block in the left text; zero for an addition
 * @param rightStart the first line of the block in the right text
 * @param rightCount the number of lines of the block in the right text; zero for a deletion
 * @since 1.0
 */
public record Difference(Kind kind, int leftStart, int leftCount, int rightStart, int rightCount) {

    /**
     * What happened to the lines of a block, going from the left text to the right one.
     *
     * @since 1.0
     */
    public enum Kind {
        /** Lines of the right text that the left one does not have. */
        ADD,
        /** Lines of the left text that the right one does not have. */
        DELETE,
        /** Lines of the left text replaced by other lines in the right one. */
        CHANGE
    }

    /** Checks that the block fits its kind and has no negative part. */
    public Difference {
        if (kind == null) {
            throw new IllegalArgumentException("kind");
        }
        if (leftStart < 0 || leftCount < 0 || rightStart < 0 || rightCount < 0) {
            throw new IllegalArgumentException("negative line or count: " + leftStart + ", " + leftCount + ", "
                    + rightStart + ", " + rightCount);
        }
        if (leftStart > Integer.MAX_VALUE - leftCount || rightStart > Integer.MAX_VALUE - rightCount) {
            throw new IllegalArgumentException("a block that ends past the last line there can be: " + leftStart
                    + ", " + leftCount + ", " + rightStart + ", " + rightCount);
        }
        boolean fits = switch (kind) {
            case ADD -> leftCount == 0 && rightCount > 0;
            case DELETE -> leftCount > 0 && rightCount == 0;
            case CHANGE -> leftCount > 0 && rightCount > 0;
        };
        if (!fits) {
            throw new IllegalArgumentException(kind + " of " + leftCount + " left and " + rightCount + " right lines");
        }
    }

    /** The line after the last one of the block in the left text. */
    public int leftEnd() {
        return leftStart + leftCount;
    }

    /** The line after the last one of the block in the right text. */
    public int rightEnd() {
        return rightStart + rightCount;
    }
}
