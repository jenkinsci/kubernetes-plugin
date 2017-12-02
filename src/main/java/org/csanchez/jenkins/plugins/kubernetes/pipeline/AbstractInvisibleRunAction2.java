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

import edu.umd.cs.findbugs.annotations.NonNull;
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

    protected transient Run<?, ?> run;

    public Run<?, ?> getRun() {
        return run;
    }

    protected void setRun(Run<?, ?> run) {
        this.run = run;
    }

    protected static void push(@NonNull Run<?, ?> run, @NonNull Class<? extends AbstractInvisibleRunAction2> clazz,
            @NonNull String item) throws IOException {
        synchronized (run) {
            BulkChange bc = new BulkChange(run);
            try {
                AbstractInvisibleRunAction2 action = run.getAction(clazz);
                if (action == null) {
                    action = clazz.newInstance();
                    run.addAction(action);
                }
                LOGGER.log(Level.FINEST, "Pushing item {0} to action {1} in run {2}",
                        new Object[] { item, action, run });
                action.stack.push(item);
                bc.commit();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Can not instantiate class " + clazz, e);
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
