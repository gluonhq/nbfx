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
package org.netbeans.modules.nbfx.ide.diff;

import org.netbeans.api.nbfx.editor.EditorDocument;
import org.netbeans.api.nbfx.editor.EditorService;
import org.openide.filesystems.FileObject;

/**
 * An editor service that has no editor to give: what a test of the diff that is not about the
 * editors installs, so that it runs as without them whatever else is there to make them - the
 * editor module is, for the tests that edit a file in a diff.
 */
public final class NoEditors implements EditorService {

    @Override
    public EditorDocument createDocument(FileObject file) {
        return null;
    }
}
