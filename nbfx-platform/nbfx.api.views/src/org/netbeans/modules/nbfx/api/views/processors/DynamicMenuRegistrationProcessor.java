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
import org.netbeans.api.nbfx.views.DynamicMenuRegistration;
import org.netbeans.api.nbfx.views.DynamicMenuRegistrations;
import org.openide.filesystems.annotations.LayerBuilder.File;
import org.openide.filesystems.annotations.LayerGeneratingProcessor;
import org.openide.filesystems.annotations.LayerGenerationException;
import org.openide.util.lookup.ServiceProvider;

/**
 * Generates the dynamic-menu layer entry at the menu's reference point:
 * {@code NbFx/<path>/<id>.dynamic} with the factory class, position and separator.
 *
 * @since 1.0
 */
@ServiceProvider(service = Processor.class)
public class DynamicMenuRegistrationProcessor extends LayerGeneratingProcessor {

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                DynamicMenuRegistration.class.getCanonicalName(),
                DynamicMenuRegistrations.class.getCanonicalName());
    }

    @Override
    protected boolean handleProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv)
            throws LayerGenerationException {
        if (roundEnv.processingOver()) {
            return false;
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DynamicMenuRegistration.class)) {
            DynamicMenuRegistration registration = element.getAnnotation(DynamicMenuRegistration.class);
            if (registration != null) {
                register(element, registration);
            }
        }
        for (Element element : roundEnv.getElementsAnnotatedWith(DynamicMenuRegistrations.class)) {
            DynamicMenuRegistrations registrations = element.getAnnotation(DynamicMenuRegistrations.class);
            if (registrations != null) {
                for (DynamicMenuRegistration registration : registrations.value()) {
                    register(element, registration);
                }
            }
        }
        return true;
    }

    private void register(Element element, DynamicMenuRegistration registration)
            throws LayerGenerationException {
        if (!(element instanceof TypeElement type)) {
            throw new LayerGenerationException("@DynamicMenuRegistration is only valid on a type", element);
        }
        String factory = processingEnv.getElementUtils().getBinaryName(type).toString();
        String path = registration.path();
        String filePath = "NbFx/" + path + "/" + registration.id() + LayerPaths.DYNAMIC_SUFFIX;
        File file = layer(element).file(filePath);
        file.stringvalue("factory", factory);
        file.intvalue("position", registration.position());
        file.boolvalue("separatorBefore", registration.separatorBefore());
        if (!registration.displayName().isEmpty()) {
            file.bundlevalue("displayName", registration.displayName());
        }
        file.write();
    }
}
