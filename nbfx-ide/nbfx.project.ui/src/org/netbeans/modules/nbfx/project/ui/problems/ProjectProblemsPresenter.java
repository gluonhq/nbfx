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
package org.netbeans.modules.nbfx.project.ui.problems;

import javafx.stage.Window;

/**
 * The UI seam of {@link NbfxProjectProblems}: it lets the service decide when an alert or resolver
 * is needed while tests substitute a recorder instead of opening modal windows. The production
 * implementation shows {@link ProjectProblemsDialog} and {@link ProjectProblemsCustomizer}.
 */
interface ProjectProblemsPresenter {

    /**
     * Shows the alert for the problems in {@code model}.
     *
     * @return {@code true} when the user chose to open the resolver
     */
    boolean showAlert(Window owner, ProjectProblemsModel model);

    /** Shows the resolver for the problems in {@code model}. */
    void showCustomizer(Window owner, ProjectProblemsModel model);
}
