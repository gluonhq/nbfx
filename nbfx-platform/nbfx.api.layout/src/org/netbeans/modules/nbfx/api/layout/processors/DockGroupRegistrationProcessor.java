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
package org.netbeans.modules.nbfx.api.layout.processors;

import java.util.HashSet;
import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layout.DockGroupId;
import org.netbeans.api.nbfx.layout.DockGroupRegistration;
import org.netbeans.api.nbfx.layout.DockGroupRegistrations;
import org.netbeans.api.nbfx.layout.PaneRole;
import org.netbeans.api.nbfx.layout.PresentationId;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/DockGroups/<id>} file per {@link DockGroupRegistration}.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class DockGroupRegistrationProcessor extends LayerGeneratingProcessor {

    private final Set<String> emittedIds = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                DockGroupRegistration.class.getCanonicalName(),
                DockGroupRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DockGroupRegistration.class)) {
            DockGroupRegistration registration = element.getAnnotation(DockGroupRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DockGroupRegistrations.class)) {
            DockGroupRegistrations registrations = element.getAnnotation(DockGroupRegistrations.class);
            if (registrations != null) {
                for (DockGroupRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, DockGroupRegistration registration)
            throws LayerGenerationException {
        DockGroupId id;
        PaneRole area;
        PresentationId presentation = null;
        try {
            id = DockGroupId.of(registration.id());
            area = PaneRole.of(registration.area());
            if (!registration.presentation().isBlank()) {
                presentation = PresentationId.of(registration.presentation());
            }
        } catch (IllegalArgumentException ex) {
            throw new LayerGenerationException("Invalid @DockGroupRegistration id: " + ex.getMessage(), element);
        }
        if (!emittedIds.add(id.value())) {
            throw new LayerGenerationException("Duplicate @DockGroupRegistration id: " + id, element);
        }
        LayerBuilder.File file = layer(element).file(LayerPaths.DOCK_GROUPS + "/" + id.value());
        file.stringvalue("area", area.value());
        file.stringvalue("kind", registration.kind().name());
        if (presentation != null) {
            file.stringvalue("presentation", presentation.value());
        }
        file.stringvalue("capacity", registration.capacity().name());
        if (!registration.policyId().isBlank()) {
            file.stringvalue("policyId", registration.policyId());
        }
        file.stringvalue("lifetime", registration.lifetime().name());
        file.write();
    }
}
