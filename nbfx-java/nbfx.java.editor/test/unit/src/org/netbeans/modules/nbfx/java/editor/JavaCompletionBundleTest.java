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
package org.netbeans.modules.nbfx.java.editor;

import java.util.List;
import java.util.ResourceBundle;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * The Java completion labels must live in java.editor's own bundle, not in a package of another
 * module. The runtime module system refuses cross-module package resources that are not exported.
 */
public class JavaCompletionBundleTest {

    @Test
    public void completionLabelsAreInTheJavaEditorBundle() {
        ResourceBundle bundle = ResourceBundle.getBundle("org.netbeans.modules.nbfx.java.editor.Bundle");
        for (String key : List.of(
                "completion.popup.lexer.import",
                "completion.popup.lexer.keyword",
                "completion.popup.lexer.source",
                "completion.popup.lexer.literal",
                "completion.popup.module")) {
            assertFalse(key, bundle.getString(key).isBlank());
        }
    }
}
