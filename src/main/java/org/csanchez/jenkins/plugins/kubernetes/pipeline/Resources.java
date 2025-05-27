/*
 * Copyright (C) 2017 Original Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;

public class Resources {

    private static final Logger LOGGER = Logger.getLogger(ContainerStepExecution.class.getName());

    /**
     * Close each {@link Closeable}, capturing and ignoring any {@link IOException} thrown.
     * @param context step context
     * @param closeables list of closable resources to close
     */
    public static void closeQuietly(@NonNull StepContext context, @NonNull Closeable... closeables) {
        for (Closeable c : closeables) {
            if (c != null) {
                try {
                    c.close();
                } catch (IOException e) {
                    try {
                        TaskListener listener = context.get(TaskListener.class);
                        if (listener != null) {
                            listener.error("Error while closing: [" + c + "]");
                        }
                    } catch (IOException | InterruptedException e1) {
                        LOGGER.log(Level.WARNING, "Error writing to task listener", e);
                    }
                }
            }
        }
    }

    /**
     * Factory method for a {@link BodyExecutionCallback} that closes each {@link Closeable} when
     * execution is finished.
     * @param closeables list of closable resources
     * @return new body execution callback, never null
     */
    @NonNull
    public static BodyExecutionCallback closeQuietlyCallback(@NonNull Closeable... closeables) {
        return new CloseQuietlyBodyExecutionCallback(closeables);
    }

    /**
     * Pipeline body execution callback that quietly closes all referenced {@link Closeable} when
     * {@link #finished(StepContext)} is called.
     * @see #closeQuietly(StepContext, Closeable...)
     */
    @SuppressFBWarnings("SE_BAD_FIELD")
    private static class CloseQuietlyBodyExecutionCallback extends BodyExecutionCallback.TailCall {

        private static final long serialVersionUID = 6385838254761750483L;

        private final Closeable[] closeables;

        /**
         * Create new body execution callback that closes the provided resources quietly.
         * @param closeables list of closable resources
         */
        CloseQuietlyBodyExecutionCallback(@NonNull Closeable... closeables) {
            this.closeables = closeables;
        }

        @Override
        public void finished(StepContext context) {
            closeQuietly(context, closeables);
        }
    }
}
