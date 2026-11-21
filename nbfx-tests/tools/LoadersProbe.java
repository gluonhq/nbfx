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

import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;

/**
 * Standalone probe for H.4.1: read a DataObject's lookup and cookies without touching its node
 * delegate.
 * <p>
 * Run it against the built platform distribution with class-initialization logging:
 *
 * <pre>
 * CP="netbeans/nbbuild/netbeans/platform/modules/*:netbeans/nbbuild/netbeans/platform/core/*:netbeans/nbbuild/netbeans/platform/lib/*"
 * java --add-opens java.base/java.net=ALL-UNNAMED -cp "$CP" -Xlog:class+init=info \
 *      nbfx-tests/tools/LoadersProbe.java
 * </pre>
 *
 * On the pinned upstream, the output includes {@code Initializing 'org/openide/nodes/Node'}; with
 * {@code patches/0021-loaders-node-free.patch} it does not. The probe still requires the
 * {@code --add-opens} because the platform's file system repository uses the old XML parser.
 */
public final class LoadersProbe {

    private LoadersProbe() {
    }

    public static void main(String[] args) throws Exception {
        FileObject root = FileUtil.createMemoryFileSystem().getRoot();
        FileObject file = root.createData("probe.txt");
        DataObject dataObject = DataObject.find(file);

        if (dataObject.getLookup().lookup(DataObject.class) != dataObject) {
            throw new AssertionError("the lookup does not expose the data object");
        }
        if (dataObject.getCookie(DataObject.class) != dataObject) {
            throw new AssertionError("getCookie does not expose the data object");
        }
        System.out.println("DataObject lookup and cookie OK: " + dataObject.getName());
    }
}
