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
package org.netbeans.modules.nbfx.platform.shell.api;

/**
 * Selects which menus and tool bars a chrome instance shows. The main window uses {@link #ALL}; a
 * detached window supplies a reduced profile without changing the layer registrations.
 *
 * @since 1.0
 */
public interface ShellChromeProfile {

    /** The profile that includes every registered surface. */
    ShellChromeProfile ALL = new ShellChromeProfile() {
        @Override
        public boolean includeMenu(String menuId) {
            return true;
        }

        @Override
        public boolean includeMenuItem(String menuId, String itemId) {
            return true;
        }

        @Override
        public boolean includeToolBar(String toolBarId) {
            return true;
        }
    };

    /** Whether the menu folder {@code menuId} is part of this chrome. */
    boolean includeMenu(String menuId);

    /** Whether the menu item or dynamic section {@code itemId} is part of this chrome. */
    boolean includeMenuItem(String menuId, String itemId);

    /** Whether the tool bar {@code toolBarId} is part of this chrome. */
    boolean includeToolBar(String toolBarId);

    /** Whether the tool bar item {@code itemId} is part of this chrome. */
    default boolean includeToolBarItem(String toolBarId, String itemId) {
        return true;
    }

    /** Whether tool bars get a leading drag handle. */
    default boolean movableToolBars() {
        return true;
    }

    /** Whether the menu bar uses the platform menu bar. */
    default boolean useSystemMenuBar() {
        return true;
    }

    /** The all-inclusive profile. */
    static ShellChromeProfile all() {
        return ALL;
    }
}
