package org.csanchez.jenkins.plugins.kubernetes;

import hudson.model.Executor;
import hudson.model.Queue;
import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.slaves.SlaveComputer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KubernetesLauncherTest {

	@Mock
	private SlaveComputer computer;

	@Mock
	private TaskListener listener;

	@Test
	public void givenDisconnectedComputerShouldAbortRunningExecutors() {
		KubernetesLauncher launcher = new KubernetesLauncher();
		final FakeExecutor fakeExecutor = new FakeExecutor();
		Executor executor = fakeExecutor.getMockExecutor();

		when(computer.getDisplayName()).thenReturn("pod-0");
		when(computer.getExecutors()).thenReturn(Collections.singletonList(executor));

		launcher.afterDisconnect(computer, listener);

		fakeExecutor.assertWasAborted();
	}

	private static class FakeExecutor {

		private final Executor mockExecutor = mock(Executor.class);
		private Result lastResult;

		final Answer<Void> answer = new Answer<>() {
			@Override
			public Void answer(InvocationOnMock invocation) {
				lastResult = Result.ABORTED;
				return null;
			}
		};

		public FakeExecutor() {
			Queue.Task task = mock(Queue.Task.class);
			final Queue.Executable executable = mock(Queue.Executable.class);

			when(mockExecutor.getCurrentExecutable()).thenReturn(executable);
			when(executable.getParent()).thenReturn(task);
			when(task.getOwnerTask()).thenReturn(task);

			doAnswer(answer).when(mockExecutor).interrupt(eq(Result.ABORTED), any());
		}

		public Executor getMockExecutor() {
			return mockExecutor;
		}

		public void assertWasAborted() {
			assertEquals(Result.ABORTED, lastResult);
		}
	}
}
