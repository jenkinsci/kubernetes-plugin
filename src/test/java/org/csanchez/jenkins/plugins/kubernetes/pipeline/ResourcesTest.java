package org.csanchez.jenkins.plugins.kubernetes.pipeline;

import static org.mockito.Mockito.*;

import hudson.AbortException;
import hudson.model.TaskListener;
import java.io.Closeable;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.jupiter.api.Test;

class ResourcesTest {

    @Test
    void testCloseQuietly() throws Exception {
        StepContext ctx = mock(StepContext.class);
        TaskListener listener = mock(TaskListener.class);
        when(ctx.get(TaskListener.class))
                .thenReturn(listener)
                .thenThrow(IOException.class)
                .thenThrow(InterruptedException.class)
                .thenReturn(null);
        Closeable c1 = mock(Closeable.class);
        doThrow(IOException.class).when(c1).close();
        Closeable c2 = mock(Closeable.class);
        doThrow(IOException.class).when(c2).close();
        Closeable c3 = mock(Closeable.class);
        doThrow(IOException.class).when(c3).close();
        Closeable c4 = mock(Closeable.class);

        // test
        Resources.closeQuietly(ctx, c1, c2, null, c3, c4);

        // verify
        verify(c1).close();
        verify(c2).close();
        verify(c3).close();
        verify(c4).close();
        verify(ctx, times(3)).get(TaskListener.class);
        verify(listener).error(any());
    }

    @Test
    void testCloseQuietlyCallbackOnSuccess() throws Exception {
        StepContext ctx = mock(StepContext.class);
        Closeable c1 = mock(Closeable.class);
        doThrow(IOException.class).when(c1).close();
        Closeable c2 = mock(Closeable.class);

        // test
        BodyExecutionCallback callback = Resources.closeQuietlyCallback(c1, c2);
        callback.onSuccess(ctx, "done");

        // verify
        verify(c1).close();
        verify(c2).close();
    }

    @Test
    void testCloseQuietlyCallbackOnFailure() throws Exception {
        StepContext ctx = mock(StepContext.class);
        Closeable c1 = mock(Closeable.class);
        doThrow(IOException.class).when(c1).close();
        Closeable c2 = mock(Closeable.class);

        // test
        BodyExecutionCallback callback = Resources.closeQuietlyCallback(c1, c2);
        callback.onFailure(ctx, new AbortException());

        // verify
        verify(c1).close();
        verify(c2).close();
    }
}
