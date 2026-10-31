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
package org.netbeans.modules.nbfx.ide.workbench;

import java.util.List;
import org.netbeans.api.nbfx.actions.ActionContext;
import org.netbeans.api.nbfx.project.OpenProject;
import org.netbeans.modules.nbfx.platform.nodes.PropertyContextType;
import org.openide.util.lookup.ServiceProvider;

/**
 * Contributes the selected project to the platform property sheet.
 */
@ServiceProvider(service = PropertyContextType.class)
public final class ProjectContextType implements PropertyContextType {

    @Override
    public List<Class<?>> contextTypes() {
        return List.of(OpenProject.class);
    }

    @Override
    public Object select(ActionContext context) {
        return context.lookup(OpenProject.class);
    }
}
