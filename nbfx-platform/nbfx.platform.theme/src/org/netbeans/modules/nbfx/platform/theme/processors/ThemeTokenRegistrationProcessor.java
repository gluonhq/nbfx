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
package org.netbeans.modules.nbfx.platform.theme.processors;

import java.util.Set;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import org.netbeans.api.nbfx.layer.LayerPaths;
import org.netbeans.api.nbfx.theme.ThemeTokenRegistration;
import org.netbeans.api.nbfx.theme.ThemeTokenRegistrations;
import org.openide.filesystems.annotations.LayerBuilder;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Writes one {@code NbFx/ThemeTokens/<id>} entry per {@link ThemeTokenRegistration}.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class ThemeTokenRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                ThemeTokenRegistration.class.getCanonicalName(),
                ThemeTokenRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ThemeTokenRegistration.class)) {
            ThemeTokenRegistration registration = element.getAnnotation(ThemeTokenRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(ThemeTokenRegistrations.class)) {
            ThemeTokenRegistrations registrations = element.getAnnotation(ThemeTokenRegistrations.class);
            if (registrations != null) {
                for (ThemeTokenRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, ThemeTokenRegistration registration)
            throws LayerGenerationException {
        if (!registration.id().matches("\\w+")) {
            throw new LayerGenerationException("@ThemeTokenRegistration id must be a word", element);
        }
        if (registration.resource().isBlank()) {
            throw new LayerGenerationException(
                    "@ThemeTokenRegistration requires a resource", element);
        }
        String owner = processingEnv.getElementUtils().getBinaryName((TypeElement) element).toString();
        LayerBuilder.File file = layer(element).file(
                LayerPaths.THEME_TOKENS + "/" + registration.id());
        file.stringvalue("owner", owner);
        file.stringvalue("resource", registration.resource());
        file.intvalue("order", registration.order());
        file.write();
    }
}
