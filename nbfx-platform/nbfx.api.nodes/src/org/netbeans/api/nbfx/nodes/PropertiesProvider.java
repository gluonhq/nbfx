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
package org.netbeans.api.nbfx.nodes;

import java.util.List;

/**
 * Contributes the property sets shown for a context object. Providers are registered in the
 * default Lookup; the property sheet asks every provider for the context it is showing.
 *
 * @since 1.0
 */
public interface PropertiesProvider {

    /**
     * The property sets for {@code context}, or an empty list when the provider has nothing to
     * offer.
     *
     * @param context the object whose properties are shown
     * @return the property sets, never {@code null}
     */
    List<PropertySetModel> getPropertySets(Object context);
}
