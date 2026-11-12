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
package org.netbeans.modules.nbfx.platform.options;

import java.util.logging.Logger;
import org.netbeans.api.nbfx.actions.AbstractCommand;
import org.netbeans.api.nbfx.actions.ActionIds;
import org.netbeans.api.nbfx.options.OptionsDisplayer;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.openide.util.Lookup;
import org.openide.util.NbBundle;

/** Window ▸ Options (Shortcut+,): opens the Options dialog. Always enabled. */
final class SelectOptionsCommand extends AbstractCommand {

    private static final Logger LOG = Logger.getLogger(SelectOptionsCommand.class.getName());

    SelectOptionsCommand() {
        super(ActionIds.SELECT_OPTIONS,
                NbBundle.getMessage(SelectOptionsCommand.class, "CTL_SelectOptionsCommand"),
                new KeyCodeCombination(KeyCode.COMMA, KeyCombination.SHORTCUT_DOWN), false);
    }

    @Override
    public void run() {
        OptionsDisplayer displayer = Lookup.getDefault().lookup(OptionsDisplayer.class);
        if (displayer == null) {
            LOG.warning("No OptionsDisplayer service; options cannot be opened");
            return;
        }
        displayer.open();
    }
}
