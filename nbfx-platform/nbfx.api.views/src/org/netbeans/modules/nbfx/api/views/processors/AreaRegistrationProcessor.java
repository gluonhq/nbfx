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
package org.netbeans.modules.nbfx.api.views.processors;

import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.views.AreaRegistration;
import org.netbeans.api.nbfx.views.AreaRegistrations;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/ViewAreas/<id>} folder per {@link AreaRegistration}.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class AreaRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                AreaRegistration.class.getCanonicalName(),
                AreaRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(AreaRegistration.class)) {
            AreaRegistration registration = element.getAnnotation(AreaRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(AreaRegistrations.class)) {
            AreaRegistrations registrations = element.getAnnotation(AreaRegistrations.class);
            if (registrations != null) {
                for (AreaRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, AreaRegistration registration)
            throws LayerGenerationException {
        if (registration.id().isBlank() || registration.displayName().isBlank()) {
            throw new LayerGenerationException(
                    "@AreaRegistration requires a non-empty id and displayName", element);
        }
        if (registration.weight() < 0 || registration.weight() > 1) {
            throw new LayerGenerationException(
                    "@AreaRegistration weight must be between 0 and 1", element);
        }
        LayerBuilder.File folder = layer(element).folder(
                LayerPaths.VIEW_AREAS + "/" + registration.id());
        folder.bundlevalue("displayName", registration.displayName());
        folder.stringvalue("location", registration.location().name());
        folder.doublevalue("weight", registration.weight());
        folder.intvalue("position", registration.position());
        folder.write();
    }
}
