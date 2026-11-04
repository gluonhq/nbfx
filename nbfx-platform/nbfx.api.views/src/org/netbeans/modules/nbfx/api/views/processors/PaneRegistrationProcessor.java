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
import org.netbeans.api.nbfx.views.PaneRegistration;
import org.netbeans.api.nbfx.views.PaneRegistrations;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/WindowPanes/<id>} entry per {@link PaneRegistration}.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class PaneRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                PaneRegistration.class.getCanonicalName(),
                PaneRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PaneRegistration.class)) {
            PaneRegistration registration = element.getAnnotation(PaneRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(PaneRegistrations.class)) {
            PaneRegistrations registrations = element.getAnnotation(PaneRegistrations.class);
            if (registrations != null) {
                for (PaneRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, PaneRegistration registration)
            throws LayerGenerationException {
        if (!registration.id().matches("\\w+")) {
            throw new LayerGenerationException(
                    "@PaneRegistration id must be a word", element);
        }
        LayerBuilder.File file = layer(element).file(
                LayerPaths.WINDOW_PANES + "/" + registration.id());
        if (!registration.displayName().isBlank()) {
            file.bundlevalue("displayName", registration.displayName());
        }
        file.stringvalue("kind", registration.kind().name());
        if (!registration.area().isBlank()) {
            file.stringvalue("area", registration.area());
        }
        file.boolvalue("permanent", registration.permanent());
        file.boolvalue("keepWhenEmpty", registration.keepWhenEmpty());
        file.intvalue("position", registration.position());
        file.write();
    }
}
