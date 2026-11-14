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

import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.openide.util.NbPreferences;

/**
 * The nbfx project problems preferences: whether an alert is shown when a broken project is
 * opened. The preference path is {@code nbfx/project/problems/showAgain}, as documented in the
 * plan (§8).
 */
final class ProjectProblemsSettings {

    private static final Logger LOG = Logger.getLogger(ProjectProblemsSettings.class.getName());
    private static final String NODE = "nbfx/project/problems"; // NOI18N
    private static final String SHOW_AGAIN = "showAgain"; // NOI18N
    private static final String SUPPRESS_PROPERTY = "BrokenReferencesSupport.suppressBrokenRefAlert"; // NOI18N

    private ProjectProblemsSettings() {
    }

    static boolean isShowAgain() {
        try {
            return preferences().getBoolean(SHOW_AGAIN, true);
        } catch (RuntimeException ex) {
            LOG.fine("Could not read the project problems preference: " + ex);
            return true;
        }
    }

    static void setShowAgain(boolean showAgain) {
        try {
            preferences().putBoolean(SHOW_AGAIN, showAgain);
        } catch (RuntimeException ex) {
            LOG.fine("Could not store the project problems preference: " + ex);
        }
    }

    /**
     * Compatibility with the upstream switch used by tests and by users who run with
     * {@code -J-DBrokenReferencesSupport.suppressBrokenRefAlert=true}.
     */
    static boolean isSuppressedByProperty() {
        return Boolean.getBoolean(SUPPRESS_PROPERTY);
    }

    private static Preferences preferences() {
        return NbPreferences.root().node(NODE);
    }
}
