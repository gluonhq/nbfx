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
package org.netbeans.api.nbfx.content;

/**
 * Recreates content from its captured state and captures it back. A product registers one factory
 * per content family; the content manager routes by the identity and kind in the state.
 *
 * @since 1.1
 */
public interface ContentFactory {

    /**
     * Recreates the content described by {@code state}.
     *
     * @param state the captured state
     * @return a new content with the stated identity, instance key and restored payload
     * @throws ContentCreationException when the content cannot be recreated, for example because its
     *                                  file or resource disappeared
     */
    Content create(ContentState state) throws ContentCreationException;

    /**
     * Captures {@code content} into the state that {@link #create(ContentState)} consumes.
     *
     * @param content the content to capture
     * @return the state, never {@code null}
     */
    ContentState capture(Content content);
}