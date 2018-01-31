/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.csanchez.jenkins.plugins.kubernetes.pipeline.proc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecProc;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import hudson.Proc;

/**
 * Proc that wraps a cached Proc
 */
@Restricted(NoExternalUse.class)
public class CachedProc extends Proc {

    private ContainerExecProc proc;

    public CachedProc(ContainerExecProc proc) {
        this.proc = proc;
    }

    @Override
    public boolean isAlive() throws IOException, InterruptedException {
        return false;
    }

    @Override
    public void kill() throws IOException, InterruptedException {
    }

    @Override
    public int join() throws IOException, InterruptedException {
        return proc.isAlive() ? 0 : -1;
    }

    @Override
    public InputStream getStdout() {
        return null;
    }

    @Override
    public InputStream getStderr() {
        return null;
    }

    @Override
    public OutputStream getStdin() {
        return null;
    }
}
