package org.csanchez.jenkins.plugins.kubernetes.pipeline.kubectl;

import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import hudson.EnvVars;

final class KubeConfigExpander extends EnvironmentExpander {

   private static final long serialVersionUID = 1;
   private static final String KUBECONFIG = "KUBECONFIG";

   private final Map<String, String> overrides;

   KubeConfigExpander(String path) {
       this.overrides = new HashMap<>();
       this.overrides.put(KUBECONFIG, path);
   }

   @Override
   public void expand(EnvVars env) throws IOException, InterruptedException {
       env.overrideAll(overrides);
   }
}
