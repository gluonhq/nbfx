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
package org.netbeans.api.nbfx.actions;

import java.util.Collection;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openide.util.Lookup;
import org.openide.util.LookupListener;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.test.MockLookup;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * Verifies that a {@link ContextSensitiveCommand} is re-evaluated from the global action context: it
 * starts disabled, enables when an observed context type appears, and disables again when it is
 * removed.
 */
public class ContextSensitiveCommandTest {

    private final InstanceContent content = new InstanceContent();
    private final ActionContext context = new DelegatingContext(new AbstractLookup(content));

    @Before
    public void setUp() {
        MockLookup.setInstances(context);
    }

    @After
    public void tearDown() {
        MockLookup.setInstances();
    }

    @Test
    public void followsObservedContextTypes() {
        TestCommand command = new TestCommand();
        assertTrue("no selection yet", command.isDisabled());

        content.add("selected");
        assertFalse("selection present", command.isDisabled());

        content.remove("selected");
        assertTrue("selection cleared", command.isDisabled());
    }

    @Test
    public void evaluatesTheContextDirectly() {
        content.add(42);
        TestCommand command = new TestCommand();
        command.refreshContext();
        assertFalse(command.isDisabled());
    }

    @Test
    public void scopedViewFollowsExplicitContextSnapshot() {
        InstanceContent scopedContent = new InstanceContent();
        TestCommand base = new TestCommand();
        Command scoped = base.forContext(new AbstractLookup(scopedContent));
        assertNotSame(base, scoped);
        assertTrue(scoped.isDisabled());

        scopedContent.add("selected");
        assertFalse(scoped.isDisabled());
        scopedContent.remove("selected");
        assertTrue(scoped.isDisabled());
        scoped.dispose();
        base.dispose();
    }

    @Test
    public void scopedConstructorRollsBackListenersOnFailure() {
        TrackingLookup refreshFailure = new TrackingLookup(false);
        FailingCommand first = new FailingCommand();
        assertThrows(IllegalStateException.class, () -> first.forContext(refreshFailure));
        assertEquals(1, refreshFailure.added);
        assertEquals(1, refreshFailure.removed);
        first.dispose();

        TrackingLookup registrationFailure = new TrackingLookup(true);
        FailingCommand second = new FailingCommand();
        assertThrows(IllegalStateException.class, () -> second.forContext(registrationFailure));
        assertEquals(1, registrationFailure.added);
        assertEquals(1, registrationFailure.removed);
        second.dispose();
    }

    private static final class FailingCommand extends ContextSensitiveCommand {
        FailingCommand() {
            super("failing", "Failing", String.class);
        }

        @Override
        protected boolean isEnabled(ActionContext context) {
            throw new IllegalStateException("cannot evaluate");
        }

        @Override
        public void run() {
        }
    }

    private static final class TrackingLookup extends Lookup {
        private final boolean failAdd;
        private int added;
        private int removed;

        private TrackingLookup(boolean failAdd) {
            this.failAdd = failAdd;
        }

        @Override
        public <T> T lookup(Class<T> clazz) {
            return null;
        }

        @Override
        public <T> Result<T> lookup(Template<T> template) {
            return new Result<>() {
                @Override
                public void addLookupListener(LookupListener listener) {
                    added++;
                    if (failAdd) {
                        throw new IllegalStateException("cannot register");
                    }
                }

                @Override
                public void removeLookupListener(LookupListener listener) {
                    removed++;
                }

                @Override
                public Collection<? extends T> allInstances() {
                    return List.of();
                }
            };
        }
    }

    private static final class TestCommand extends ContextSensitiveCommand {
        TestCommand() {
            super("test", "Test", String.class, Integer.class);
        }

        @Override
        protected boolean isEnabled(ActionContext context) {
            return context.lookup(String.class) != null || context.lookup(Integer.class) != null;
        }

        @Override
        public void run() {
            // no-op
        }
    }

    private static final class DelegatingContext extends ActionContext {
        private final Lookup delegate;

        DelegatingContext(Lookup delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> T lookup(Class<T> clazz) {
            return delegate.lookup(clazz);
        }

        @Override
        public <T> Result<T> lookup(Template<T> template) {
            return delegate.lookup(template);
        }
    }
}
