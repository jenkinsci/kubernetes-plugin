/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import java.io.IOException;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.BulkChange;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import jenkins.model.RunAction2;

/**
 * @author Carlos Sanchez
 * @since 1.1.1
 *
 */
public abstract class AbstractInvisibleRunAction2 extends InvisibleAction implements RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(AbstractInvisibleRunAction2.class.getName());

    protected final Stack<String> stack = new Stack<>();

    private transient Run<?, ?> run;

    public AbstractInvisibleRunAction2(Run<?, ?> run) {
        setRun(run);
    }

    protected abstract AbstractInvisibleRunAction2 createAction(Run<?, ?> run);

    public Run<?, ?> getRun() {
        return run;
    }

    protected void setRun(Run<?, ?> run) {
        this.run = run;
    }

    public void push(String item) throws IOException {
        if (run == null) {
            LOGGER.warning("run is null, cannot push");
            return;
        }
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                AbstractInvisibleRunAction2 action = run.getAction(this.getClass());
                if (action == null) {
                    action = createAction(run);
                    run.addAction(action);
                }
                LOGGER.log(Level.FINEST, "Pushing item {0} to action {1} in run {2}",
                        new Object[] { item, action, run });
                action.stack.push(item);
                bc.commit();
            } finally {
                bc.abort();
            }
        }
    }

    public String pop() throws IOException {
        if (run == null) {
            LOGGER.warning("run is null, cannot pop");
            return null;
        }
        synchronized (getRun()) {
            BulkChange bc = new BulkChange(getRun());
            try {
                AbstractInvisibleRunAction2 action = getRun().getAction(this.getClass());
                if (action == null) {
                    action = createAction(getRun());
                    getRun().addAction(action);
                }
                String item = action.stack.pop();
                LOGGER.log(Level.FINEST, "Popped item {0} from action {1} in run {2}",
                        new Object[] { item, action, run });
                bc.commit();
                return item;
            } finally {
                bc.abort();
            }
        }
    }

    @Override
    public void onAttached(Run<?, ?> r) {
        setRun(r);
    }

    @Override
    public void onLoad(Run<?, ?> r) {
        setRun(r);
    }

}
