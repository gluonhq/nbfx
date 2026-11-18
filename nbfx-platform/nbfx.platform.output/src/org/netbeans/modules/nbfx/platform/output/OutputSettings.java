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
package org.netbeans.modules.nbfx.platform.output;

import java.util.prefs.Preferences;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import org.openide.util.NbPreferences;

/**
 * The look every console shares, as in NetBeans, where Wrap Text and Larger / Smaller Font apply to
 * the whole Output window: the font size and the line wrapping, kept in the module's preferences.
 * Used on the JavaFX Application Thread.
 */
final class OutputSettings {

    static final double DEFAULT_FONT_SIZE = 13;
    static final double MIN_FONT_SIZE = 8;
    static final double MAX_FONT_SIZE = 40;

    private static final String KEY_FONT_SIZE = "fontSize";
    private static final String KEY_WRAP = "wrapText";

    private static OutputSettings instance;

    private final DoubleProperty fontSize = new SimpleDoubleProperty(this, "fontSize", DEFAULT_FONT_SIZE);
    private final BooleanProperty wrapText = new SimpleBooleanProperty(this, "wrapText");

    /** Settings that are not saved, for tests. */
    OutputSettings() {
    }

    /** The settings of the Output window, read from the preferences and saved as they change. */
    static synchronized OutputSettings getDefault() {
        if (instance == null) {
            OutputSettings settings = new OutputSettings();
            Preferences prefs = NbPreferences.forModule(OutputSettings.class);
            settings.fontSize.set(clamp(prefs.getDouble(KEY_FONT_SIZE, DEFAULT_FONT_SIZE)));
            settings.wrapText.set(prefs.getBoolean(KEY_WRAP, false));
            settings.fontSize.addListener((obs, was, now) -> prefs.putDouble(KEY_FONT_SIZE, now.doubleValue()));
            settings.wrapText.addListener((obs, was, now) -> prefs.putBoolean(KEY_WRAP, now));
            instance = settings;
        }
        return instance;
    }

    /** The size of the console font, in pixels. */
    DoubleProperty fontSizeProperty() {
        return fontSize;
    }

    /** Whether long lines wrap at the edge of the view. */
    BooleanProperty wrapTextProperty() {
        return wrapText;
    }

    /** One pixel larger, up to {@link #MAX_FONT_SIZE}. */
    void largerFont() {
        fontSize.set(clamp(fontSize.get() + 1));
    }

    /** One pixel smaller, down to {@link #MIN_FONT_SIZE}. */
    void smallerFont() {
        fontSize.set(clamp(fontSize.get() - 1));
    }

    private static double clamp(double size) {
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, size));
    }
}
