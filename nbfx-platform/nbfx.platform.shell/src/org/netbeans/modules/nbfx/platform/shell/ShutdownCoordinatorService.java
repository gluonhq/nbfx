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
package org.netbeans.modules.nbfx.platform.shell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.netbeans.modules.nbfx.platform.shell.api.shutdown.ShutdownCoordinator;
import org.netbeans.modules.nbfx.platform.shell.api.shutdown.ShutdownImpact;
import org.netbeans.modules.nbfx.platform.shell.api.shutdown.ShutdownParticipant;
import org.netbeans.modules.nbfx.platform.shell.api.shutdown.ShutdownSelection;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

/**
 * Default coordinator over the {@link ShutdownParticipant}s registered in the global lookup.
 * {@link #impact()} and {@link #confirm(ShutdownImpact)} run on the FX thread; the shell calls
 * {@link #shutdown(ShutdownSelection)} off it so blocking cleanup does not freeze the UI.
 */
@ServiceProvider(service = ShutdownCoordinator.class)
public final class ShutdownCoordinatorService implements ShutdownCoordinator {

    private static final Logger LOG = Logger.getLogger(ShutdownCoordinatorService.class.getName());
    private final AtomicBoolean completed = new AtomicBoolean();

    @Override
    public ShutdownImpact impact() {
        ShutdownImpact.Builder builder = ShutdownImpact.builder();
        for (ShutdownParticipant participant : participants()) {
            try {
                participant.contributeImpact(builder);
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Shutdown participant " + participant.getClass().getName()
                        + " could not describe its impact", ex);
            }
        }
        return builder.build();
    }

    @Override
    public ShutdownSelection confirm(ShutdownImpact impact) {
        return ShutdownDialog.confirm(impact);
    }

    @Override
    public void shutdown(ShutdownSelection selection) {
        if (!completed.compareAndSet(false, true)) {
            return;
        }
        ShutdownSelection effective = selection == null
                ? ShutdownSelection.defaults(impact()) : selection;
        for (ShutdownParticipant participant : participants()) {
            try {
                participant.shutdown(effective);
            } catch (Exception ex) {
                LOG.log(Level.WARNING, "Shutdown participant " + participant.getClass().getName()
                        + " failed", ex);
            }
        }
    }

    private static List<ShutdownParticipant> participants() {
        List<ShutdownParticipant> result =
                new ArrayList<>(Lookup.getDefault().lookupAll(ShutdownParticipant.class));
        result.sort(Comparator.comparingInt(ShutdownParticipant::position));
        return result;
    }
}
