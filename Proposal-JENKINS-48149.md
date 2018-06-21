# Jenkins Slave Pod Retention Proposal

Jenkins Issue: [JENKINS-48149](https://issues.jenkins-ci.org/browse/JENKINS-48149)
Red Hat [OpenShift Trello Card](https://trello.com/c/mQ2LO7pl)

## User Story

As a Jenkins administrator
I would like to control how Jenkins slave pod data is retained in Kubernetes
So that developers and Kubernetes administrators can use the pod data for debugging, performance tuning, and other analysis.

## Proposal

1. Deprecate the “Apply cap only on alive pods” option.
    1. Container caps will always apply to running or pending pods.
    2. The current cap mechanism effectively applies to running/pending pods only, as the plugin deletes terminated slave pods.
2. Add a new configuration option - “Pod Retention”, with enumerated value:
    1. Never (default)
    2. On Failure
    3. Always
3. Configuration is available as a `KubernetesCloud` option, as well as a `PodTemplate` option.
    1. If the `PodTemplate` value is not set, defer to the `KubernetesCloud` value.
    2. If the `PodTemplate` value is set, override the value from `KubernetesCloud`.
    3. `KubernetesCloud` value should default to Never, and cannot be null.
4. Update slave termination process:
    1. When the master terminates a slave, first check the status of the slave’s corresponding pod.
    2. Mark the pod for deletion if:
        1. Pod Retention = “Never”
        2. Pod Retention = “On Failure” AND the pod has one of the following phases:
            1. Succeeded
            2. Running
            3. Pending
5. Ensure that if the pod is retained, the slave pod terminates with exit code 0 under normal operation.

## Proposed Implementation

1. Add option to `KubernetesCloud` and `PodTemplate`, with appropriate interfaces
    1. Web configuration for `KubernetesCloud`
    2. Web configuration for `PodTemplate`
    3. XML data bindings for `KubernetesCloud`
    4. Groovy DSL for `PodTemplate`
2. Use configuration option to check if pod should be deleted. Options:
    1. Directly modify `KubernetesSlave`'s `_terminate` behavior.
    2. Refactor such that the pod deletion behavior is guided by a `RetentionStrategy` instance.
3. Prior to disconnect, call to the slave JNLP Engine and signal that it should not attempt to reconnect to the master. [1](#footnote-1)
    1. As above, modify `_terminate` behavior or refactor to a `RetentionStrategy`.

## Risks and Considerations

1. User impact of deprecating the “Apply cap only on alive pods” option.
2. Communicating impact of setting Pod Retention to “Always”:
    1. Every build that uses a pod template will create its own Kubernetes pod.
    2. Terminated pods are not hidden in typical Kubernetes deployments.
    3. Kubernetes developers/admins will be responsible for cleaning up old build pods.
    4. Consumers of the plugin may need to update their own documentation accordingly. [2](#footnote-2)

----

<a name="footnote-1"></a> 1. In my initial [proof of concept](https://github.com/adambkaplan/kubernetes-plugin/tree/feature/keep-pods), I discovered that the slave JNLP Engine has a health check loop that attempts to reconnect to the master when the connection drops.
If reconnects are not blocked, the slave exits with error code 255, which leaves the slave pod with the final status “Error”.

<a name="footnote-2"></a> 2. This is the case for OpenShift.