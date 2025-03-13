## Unreleased

* [JENKINS-73826](https://issues.jenkins.io/browse/JENKINS-73826) - Fix deadlock in KubernetesLauncher by removing nested synchronization and ensuring isLaunchSupported() doesn't require locks.