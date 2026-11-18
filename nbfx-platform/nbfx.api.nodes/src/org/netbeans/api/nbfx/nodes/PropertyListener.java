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

/**
 * Listens for value changes of a {@link PropertyModel}.
 *
 * @since 1.0
 */
@FunctionalInterface
public interface PropertyListener {

    /**
     * Called after a property value changed.
     *
     * @param property the property that changed
     * @param oldValue its previous value
     * @param newValue its current value
     */
    void changed(PropertyModel property, Object oldValue, Object newValue);
}
