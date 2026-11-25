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
package org.netbeans.api.nbfx.layout;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.Test;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Tests deterministic layout discovery and malformed-entry isolation. */
public class LayoutRegistryTest {

    @Test
    public void readsValidLayoutsAndSkipsInvalidEntries() throws Exception {
        FileSystem fileSystem = FileUtil.createMemoryFileSystem();
        FileObject folder = fileSystem.getRoot().createFolder("Layouts");
        write(folder, "product.default.xml",
                LayoutProcessorTestSupport.sampleLayoutXml("product.default"));
        write(folder, "product.bad.xml", "<layout>");
        write(folder, "product.mismatch.xml",
                LayoutProcessorTestSupport.sampleLayoutXml("product.other"));
        folder.createData("notes.txt");

        List<LayoutSpec> layouts = LayoutRegistry.read(folder);
        assertEquals(List.of(LayoutProcessorTestSupport.sampleLayout("product.default")), layouts);
        assertThrows(UnsupportedOperationException.class,
                () -> layouts.add(LayoutProcessorTestSupport.sampleLayout("another")));
        assertTrue(LayoutRegistry.read(null).isEmpty());
        assertTrue(LayoutRegistry.readFile(folder.getFileObject("product.mismatch.xml")).isEmpty());
    }

    private static void write(FileObject folder, String name, String contents) throws IOException {
        FileObject file = folder.createData(name);
        try (OutputStream output = file.getOutputStream()) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }
}
