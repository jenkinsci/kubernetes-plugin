/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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
package org.csanchez.jenkins.plugins.kubernetes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import hudson.model.TaskListener;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.client.Watcher;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskListenerEventWatcherTest {

    @Mock
    private TaskListener listener;

    private TaskListenerEventWatcher watcher;

    @BeforeEach
    void beforeEach() {
        watcher = new TaskListenerEventWatcher("foo", listener);
    }

    @Test
    void ignoreBookmarkAction() {
        watcher.eventReceived(Watcher.Action.BOOKMARK, new Event());
        verifyNoInteractions(listener);
    }

    @Test
    void ignoreErrorAction() {
        watcher.eventReceived(Watcher.Action.ERROR, null);
        verifyNoInteractions(listener);
    }

    @Test
    void logEventMessage() {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(bos);
        when(listener.getLogger()).thenReturn(ps);
        Event event = new Event();
        event.setMessage("cat\ndog");
        event.setReason("because");
        event.setType("Update");
        ObjectReference involvedObj = new ObjectReference();
        involvedObj.setName("foo-123");
        involvedObj.setNamespace("bar");
        event.setInvolvedObject(involvedObj);

        watcher.eventReceived(Watcher.Action.ADDED, event);

        verify(listener).getLogger();
        ps.flush();
        String output = bos.toString(StandardCharsets.UTF_8);
        assertEquals("[Update][bar/foo-123][because] cat\n[Update][bar/foo-123][because] dog\n", output);
    }
}
