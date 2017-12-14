package org.csanchez.jenkins.plugins.kubernetes.pipeline.kubectl;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.Run;

import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import org.csanchez.jenkins.plugins.kubernetes.KubectlBuildWrapper;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nonnull;

public class KubectlBuildWrapperStep extends Step {

  @DataBoundSetter
  public String serverUrl;

  @DataBoundSetter
  public String credentialsId;

  @DataBoundSetter
  public String caCertificate;

  @DataBoundConstructor
  public KubectlBuildWrapperStep() {
  }

  @Override
  public final StepExecution start(StepContext context) throws Exception {
    return new ExecutionImpl(this, context);
  }

  public static class ExecutionImpl extends AbstractStepExecutionImpl {

    private static final long serialVersionUID = 1L;

    private transient KubectlBuildWrapperStep step;

    public ExecutionImpl(KubectlBuildWrapperStep step, StepContext context) {
      super(context);
      this.step = step;
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public boolean start() throws Exception {
      Run<?,?> run = getContext().get(Run.class);

      FilePath workspace = getContext().get(FilePath.class);
      Launcher launcher = getContext().get(Launcher.class);

      KubectlBuildWrapper kubectlBuildWrapper = new KubectlBuildWrapper(step.serverUrl, step.credentialsId, step.caCertificate);

      // TODO: Clarify if that's the right thing to do
      if(!workspace.exists()){
        workspace.mkdirs();
      }
      String configFile = kubectlBuildWrapper.writeKubeConfig(workspace, launcher, run);

      getContext().newBodyInvoker()
        .withContext(EnvironmentExpander.merge(getContext().get(EnvironmentExpander.class), new KubeConfigExpander(configFile)))
        .withCallback(new Callback(configFile))
        .start();

      return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
      getContext().onFailure(cause);
    }

  }

  private static final class Callback extends BodyExecutionCallback.TailCall {

		private final String configFile;

		Callback(String configFile) {
			this.configFile = configFile;
		}

		protected void finished(StepContext context) throws Exception {
      context.get(FilePath.class).child(configFile).delete();
      context.get(TaskListener.class).getLogger().println("kubectl configuration cleaned up");
		}

		private static final long serialVersionUID = 1L;

	}

  @Extension
  public static class DescriptorImpl extends StepDescriptor {

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
      return "Setup Kubernetes CLI (kubectl)";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getFunctionName() {
      return "withKubeConfig";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean takesImplicitBlockArgument() {
      return true;
    }

    @Override
    public Set<? extends Class<?>> getRequiredContext() {
      return new HashSet<>();
    }
  }
}
