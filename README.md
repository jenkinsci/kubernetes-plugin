Kubernetes plugin for Jenkins
=========================

[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/kubernetes.svg)](https://plugins.jenkins.io/kubernetes)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/kubernetes-plugin.svg?label=release)](https://github.com/jenkinsci/kubernetes-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/kubernetes.svg?color=blue)](https://plugins.jenkins.io/kubernetes)

Jenkins plugin to run dynamic agents in a Kubernetes cluster.

Based on the [Scaling Docker with Kubernetes](http://www.infoq.com/articles/scaling-docker-with-kubernetes) article,
automates the scaling of Jenkins agents running in Kubernetes.

The plugin creates a Kubernetes Pod for each agent started, and stops it after each build.

Agents are launched as inbound agents, so it is expected that the container connects automatically to the Jenkins controller.
For that some environment variables are automatically injected:

* `JENKINS_URL` : Jenkins web interface url
* `JENKINS_SECRET` : the secret key for authentication
* `JENKINS_AGENT_NAME` : the name of the Jenkins agent
* `JENKINS_NAME` : the name of the Jenkins agent (Deprecated. Only here for backwards compatibility)

Tested with [`jenkins/inbound-agent`](https://hub.docker.com/r/jenkins/inbound-agent),
see the [Docker image source code](https://github.com/jenkinsci/docker-inbound-agent).

It is not required to run the Jenkins controller inside Kubernetes.

# 📜 Table of Contents

- [Generic setup](#generic-setup)
- [Usage](#usage)
- [Configuration reference](#configuration-reference)
- [Inheritance](#inheritance)
- [Declarative Pipeline](#declarative-pipeline)
- [Misc.](#misc.)
- [Running on OpenShift](#running-on-openshift)
- [Features controlled using system properties](#features-controlled-using-system-properties)
- [Windows support](#windows-support)
- [Constraints](#constraints)
- [Configuration on minikube](#configuration-on-minikube)
- [Configuration on Google Container Engine](#configuration-on-google-container-engine)
- [Troubleshooting 🔨](#troubleshooting)
- [Building and Testing](#building-and-testing)
- [Docker image](#docker-image)
- [Running in Kubernetes](#running-in-kubernetes)
- [Related Projects](#related-projects)

# Generic Setup
## Prerequisites
* A running Kubernetes cluster 1.14 or later. For OpenShift users, this means OpenShift Container Platform 4.x.
* A Jenkins instance installed
* The Jenkins Kubernetes plugin installed

## Configuration

Fill in the Kubernetes plugin configuration.
In order to do that, you will open the Jenkins UI and navigate to **Manage Jenkins -> Manage Nodes and Clouds -> Configure Clouds -> Add a new cloud -> Kubernetes** and enter the *Kubernetes URL* and *Jenkins URL* appropriately, unless Jenkins is running in Kubernetes in which case the defaults work.

Supported credentials include:

* Username/password
* Secret File (kubeconfig file)
* Secret text (Token-based authentication) (OpenShift)
* Google Service Account from private key (GKE authentication)
* X.509 Client Certificate

If you check **WebSocket** then agents will connect over HTTP(S) rather than the Jenkins service TCP port.
This is unnecessary when the Jenkins controller runs in the same Kubernetes cluster,
but can greatly simplify setup when agents are in an external cluster
and the Jenkins controller is not directly accessible (for example, it is behind a reverse proxy or a ingress resource).
See [JEP-222](https://jenkins.io/jep/222) for more.

To test this connection is successful you can use the **Test Connection** button to ensure there is
adequate communication from Jenkins to the Kubernetes cluster, as seen below

![image](images/cloud-configuration.png)

In addition to that, in the **Kubernetes Pod Template** section, we need to configure the image that will be used to 
spin up the agent pod. We do not recommend overriding the `jnlp` container except under unusual circumstances. 
For your agent, you can use the default Jenkins agent image available in [Docker Hub](https://hub.docker.com). In the
‘Kubernetes Pod Template’ section you need to specify the following (the rest of the configuration is up to you):
Kubernetes Pod Template Name - can be any and will be shown as a prefix for unique generated agent’ names, which will 
be run automatically during builds
Docker image - the docker image name that will be used as a reference to spin up a new Jenkins agent, as seen below

![image](images/pod-template-configuration.png)



> **Notes:**
> 
> - If your Jenkins controller is outside the cluster and uses a self-signed HTTPS certificate,
>   you will need some [additional configuration](#using-websockets-with-a-jenkins-master-with-self-signed-https-certificate).
> - Be aware that there is a current bug in Jenkins which affects the resuming of builds during restarts of the controller when using WebSockets: [JENKINS-67062](https://issues.jenkins.io/browse/JENKINS-67062).

### Restricting what jobs can use your configured cloud

Clouds can be configured to only allow certain jobs to use them.

To enable this, in your cloud's advanced configuration check the
`Restrict pipeline support to authorized folders` box. For a job to then
use this cloud configuration you will need to add it in the jobs folder's configuration.

# Usage
## Overview

The Kubernetes plugin allocates Jenkins agents in Kubernetes pods. Within these pods, there is always one special
container `jnlp` that is running the Jenkins agent. Other containers can run arbitrary processes of your choosing,
and it is possible to run commands dynamically in any container in the agent pod. 

## Using a label

Pod templates defined using the user interface declare a label. When a freestyle job or a pipeline job using
`node('some-label')` uses a label declared by a pod template, the Kubernetes Cloud allocates a new pod to run the
Jenkins agent.

It should be noted that the main reason to use the global pod template definition is to migrate a huge corpus of
existing projects (including freestyle) to run on Kubernetes without changing job definitions.
New users setting up new Kubernetes builds should use the `podTemplate` step as shown in the example snippets
[here](examples).

## Using the pipeline step

The `podTemplate` step defines an ephemeral pod template. It is created while the pipeline execution is within the
`podTemplate` block. It is immediately deleted afterwards. Such pod templates are not intended to be shared with other
builds or projects in the Jenkins instance.

The following idiom creates a pod template with a generated unique label (available as `POD_LABEL`) and runs commands inside it.

```groovy
podTemplate {
    node(POD_LABEL) {
        // pipeline steps...
    }
}
```

Commands will be executed by default in the `jnlp` container, where the Jenkins agent is running.
(The `jnlp` name is historical and is retained for compatibility.)

This will run in the `jnlp` container:
```groovy
podTemplate {
    node(POD_LABEL) {
        stage('Run shell') {
            sh 'echo hello world'
        }
    }
}
```

Find more examples in the [examples dir](examples).

The default jnlp agent image used can be customized by adding it to the template

```groovy
containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:4.7-1', args: '${computer.jnlpmac} ${computer.name}'),
```

or with the yaml syntax. Pretty much any field from the [pod model](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/) can be specified through the yaml syntax.

```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: 'jenkins/inbound-agent:4.7-1'
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
```

### Multiple containers support

Multiple containers can be defined for the agent pod, with shared resources, like mounts. Ports in each container can
be accessed as in any Kubernetes pod, by using `localhost`.

The `container` step allows executing commands into each container.

**Note**
---
Due to implementation constraints, there can be issues when executing commands in different containers if they run using different uids.
It is recommended to use the same uid across the different containers part of the same pod to avoid any issue.
---

```groovy
podTemplate(containers: [
    containerTemplate(name: 'maven', image: 'maven:3.8.1-jdk-8', command: 'sleep', args: '99d'),
    containerTemplate(name: 'golang', image: 'golang:1.16.5', command: 'sleep', args: '99d')
  ]) {

    node(POD_LABEL) {
        stage('Get a Maven project') {
            git 'https://github.com/jenkinsci/kubernetes-plugin.git'
            container('maven') {
                stage('Build a Maven project') {
                    sh 'mvn -B -ntp clean install'
                }
            }
        }

        stage('Get a Golang project') {
            git url: 'https://github.com/hashicorp/terraform.git', branch: 'main'
            container('golang') {
                stage('Build a Go project') {
                    sh '''
                    mkdir -p /go/src/github.com/hashicorp
                    ln -s `pwd` /go/src/github.com/hashicorp/terraform
                    cd /go/src/github.com/hashicorp/terraform && make
                    '''
                }
            }
        }

    }
}
```

or

```groovy
podTemplate(yaml: '''
    apiVersion: v1
    kind: Pod
    spec:
      containers:
      - name: maven
        image: maven:3.8.1-jdk-8
        command:
        - sleep
        args:
        - 99d
      - name: golang
        image: golang:1.16.5
        command:
        - sleep
        args:
        - 99d
''') {
  node(POD_LABEL) {
    stage('Get a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      container('maven') {
        stage('Build a Maven project') {
          sh 'mvn -B -ntp clean install'
        }
      }
    }

    stage('Get a Golang project') {
      git url: 'https://github.com/hashicorp/terraform-provider-google.git', branch: 'main'
      container('golang') {
        stage('Build a Go project') {
          sh '''
            mkdir -p /go/src/github.com/hashicorp
            ln -s `pwd` /go/src/github.com/hashicorp/terraform
            cd /go/src/github.com/hashicorp/terraform && make
          '''
        }
      }
    }

  }
}
```

#### `POD_CONTAINER` variable

The variable `POD_CONTAINER` contains the name of the container in the current context.
It is defined only within a `container` block.

```groovy
podTemplate(containers: […]) {
  node(POD_LABEL) {
    stage('Run shell') {
      container('mycontainer') {
        sh "echo hello from $POD_CONTAINER" // displays 'hello from mycontainer'
      }
    }
  }
}
```

# Configuration reference
## Pod template

Pod templates are used to create agents. They can be either configured via the user interface, or in a pipeline, using
the `podTemplate` step.
Either way it provides access to the following fields:

* **cloud** The name of the cloud as defined in Jenkins settings. Defaults to `kubernetes`
* **name** The name of the pod. This is only used for inheritance.
* **namespace** The namespace of the pod.
* **label** The node label. This is how the pod template can be referred to when asking for an agent through the `node` step. In a pipeline, it is recommended to omit this field and rely on the generated label that can be referred to using the `POD_LABEL` variable defined within the `podTemplate` block.
* **yaml** [yaml representation of the Pod](https://kubernetes.io/docs/reference/kubernetes-api/workload-resources/pod-v1/), to allow setting any values not supported as fields
* **yamlMergeStrategy** `merge()` or `override()`. Controls whether the yaml definition overrides or is merged with the yaml definition inherited from pod templates declared with `inheritFrom`. Defaults to `override()` (for backward compatibility reasons).
* **containers** The container templates part of the pod *(see below for details)*.
* **serviceAccount** The service account of the pod.
* **nodeSelector** The node selector of the pod.
* **nodeUsageMode** Either `NORMAL` or `EXCLUSIVE`, this controls whether Jenkins only schedules jobs with label expressions matching or use the node as much as possible.
* **volumes** Volumes that are defined for the pod and are mounted by **ALL** containers.
* **envVars** Environment variables that are applied to **ALL** containers.
    * **envVar** An environment variable whose value is defined inline.
    * **secretEnvVar** An environment variable whose value is derived from a Kubernetes secret.
* **imagePullSecrets** List of pull secret names, to [pull images from a private Docker registry](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/).
* **annotations** Annotations to apply to the pod.
* **inheritFrom** List of one or more pod templates to inherit from *(more details below)*.
* **slaveConnectTimeout** Timeout in seconds for an agent to be online *(more details below)*.
* **podRetention** Controls the behavior of keeping agent pods. Can be 'never()', 'onFailure()', 'always()', or 'default()' - if empty will default to deleting the pod after `activeDeadlineSeconds` has passed.
* **activeDeadlineSeconds** If `podRetention` is set to `never()` or `onFailure()`, the pod is deleted after this deadline is passed.
* **idleMinutes** Allows the pod to remain active for reuse until the configured number of minutes has passed since the last step was executed on it. Use this only when defining a pod template in the user interface.
* **showRawYaml** Enable or disable the output of the raw pod manifest. Defaults to `true`
* **runAsUser** The user ID to run all containers in the pod as.
* **runAsGroup** The group ID to run all containers in the pod as. 
* **hostNetwork** Use the hosts network.
* **workspaceVolume** The type of volume to use for the workspace.
  * `emptyDirWorkspaceVolume` (default): an empty dir allocated on the host machine
  * `dynamicPVC()` : a persistent volume claim managed dynamically. It is deleted at the same time as the pod.
  * `hostPathWorkspaceVolume()` : a host path volume
  * `nfsWorkspaceVolume()` : a nfs volume
  * `persistentVolumeClaimWorkspaceVolume()` : an existing persistent volume claim by name.

## Container template

Container templates are part of pod. They can be configured via the user interface or in a pipeline and allow you to set the following fields:

* **name** The name of the container.
* **image** The image of the container.
* **envVars** Environment variables that are applied to the container **(supplementing and overriding env vars that are set on pod level)**.
    * **envVar** An environment variable whose value is defined inline.
    * **secretEnvVar** An environment variable whose value is derived from a Kubernetes secret.
* **command** The command the container will execute. Will overwrite the Docker entrypoint. A typical value is `sleep`.
* **args** The arguments passed to the command. A typical value is `99999999`.
* **ttyEnabled** Flag to mark that tty should be enabled.
* **livenessProbe** Parameters to be added to a exec liveness probe in the container (does not support httpGet liveness probes)
* **ports** Expose ports on the container.
* **alwaysPullImage** The container will pull the image upon starting.
* **runAsUser** The user ID to run the container as.
* **runAsGroup** The group ID to run the container as.

#### Specifying a different default agent connection timeout

By default, the agent connection timeout is set to 1000 seconds. It can be customized using a system property. Please refer to the section below.

#### Using yaml to define Pod Templates

In order to support any possible value in Kubernetes `Pod` object, we can pass a yaml snippet that will be used as a base
for the template. If any other properties are set outside the YAML, they will take precedence.

```groovy
podTemplate(yaml: '''
    apiVersion: v1
    kind: Pod
    metadata:
      labels: 
        some-label: some-label-value
    spec:
      containers:
      - name: busybox
        image: busybox
        command:
        - sleep
        args:
        - 99d
    ''') {
    node(POD_LABEL) {
      container('busybox') {
        echo POD_CONTAINER // displays 'busybox'
        sh 'hostname'
      }
    }
}
```

You can use [`readFile`](https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/#readfile-read-file-from-workspace) or [`readTrusted`](https://jenkins.io/doc/pipeline/steps/coding-webhook/#readtrusted-read-trusted-file-from-scm) steps to load the yaml from a file.
Also note that in declarative pipelines the `yamlFile` can be used (see this [example](examples/declarative_from_yaml_file)).

##### Example

`pod.yaml`
```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.8.1-jdk-8
    command:
    - sleep
    args:
    - 99d
  - name: golang
    image: golang:1.16.5
    command:
    - sleep
    args:
    - 99d
```

`Jenkinsfile`
```groovy
podTemplate(yaml: readTrusted('pod.yaml')) {
  node(POD_LABEL) {
    // ...
  }
}
```

### Liveness Probe Usage
```groovy
containerTemplate(name: 'busybox', image: 'busybox', command: 'sleep', args: '99d',
                  livenessProbe: containerLivenessProbe(execArgs: 'some --command', initialDelaySeconds: 30, timeoutSeconds: 1, failureThreshold: 3, periodSeconds: 10, successThreshold: 1)
)
```
See [Defining a liveness command](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#defining-a-liveness-command) for more details.

# Inheritance

## Overview

A pod template may or may not inherit from an existing template.
This means that the pod template will inherit node selector, service account, image pull secrets, container templates
and volumes from the template it inherits from.

**yaml** is merged according to the value of `yamlMergeStrategy`.

**Service account** and **Node selector** when are overridden completely substitute any possible value found on the 'parent'.

**Container templates** that are added to the podTemplate, that has a matching containerTemplate (a container template
with the same name) in the 'parent' template, will inherit the configuration of the parent containerTemplate.
If no matching container template is found, the template is added as is.

**Volume** inheritance works exactly as **Container templates**.

**Image Pull Secrets** are combined (all secrets defined both on 'parent' and 'current' template are used).

In the example below, we will inherit from a pod template we created previously, and will just override the version of
`maven` so that it uses jdk-11 instead:

![image](images/mypod.png)

```groovy
podTemplate(inheritFrom: 'mypod', containers: [
    containerTemplate(name: 'maven', image: 'maven:3.8.1-jdk-11')
  ]) {
  node(POD_LABEL) {
    …
  }
}
```

Or in declarative pipeline

```groovy
pipeline {
  agent {
    kubernetes {
      inheritFrom 'mypod'
      yaml '''
      spec:
        containers:
        - name: maven
          image: maven:3.8.1-jdk-11
'''
      …
    }
  }
  stages {
    …
  }
}
```

Note that we only need to specify the things that are different. So, `command` and `arguments` are not specified, as
they are inherited.
Also, the `golang` container will be added as defined in the 'parent' template.

## Multiple Pod template inheritance

Field `inheritFrom` may refer a single podTemplate or multiple separated by space. In the later case each template will
be processed in the order they appear in the list *(later items overriding earlier ones)*.
In any case if the referenced template is not found it will be ignored.


## Nesting Pod templates

Field `inheritFrom` provides an easy way to compose podTemplates that have been pre-configured. In many cases it would
be useful to define and compose podTemplates directly in the pipeline using groovy.
This is made possible via nesting. You can nest multiple pod templates together in order to compose a single one.

The example below composes two different pod templates in order to create one with maven and docker capabilities.

```groovy
podTemplate(containers: [containerTemplate(image: 'docker', name: 'docker', command: 'cat', ttyEnabled: true)]) {
    podTemplate(containers: [containerTemplate(image: 'maven', name: 'maven', command: 'cat', ttyEnabled: true)]) {
      node(POD_LABEL) { // gets a pod with both docker and maven
        …
      }
    }
}
```

This feature is extra useful, pipeline library developers as it allows you to wrap pod templates into functions and let
users nest those functions according to their needs.

For example one could create functions for their podTemplates and import them for use.
Say here's our file `src/com/foo/utils/PodTemplates.groovy`:
```groovy
package com.foo.utils

public void dockerTemplate(body) {
  podTemplate(
        containers: [containerTemplate(name: 'docker', image: 'docker', command: 'sleep', args: '99d')],
        volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
    body.call()
}
}

public void mavenTemplate(body) {
  podTemplate(
        containers: [containerTemplate(name: 'maven', image: 'maven', command: 'sleep', args: '99d')],
        volumes: [secretVolume(secretName: 'maven-settings', mountPath: '/root/.m2'),
                  persistentVolumeClaim(claimName: 'maven-local-repo', mountPath: '/root/.m2repo')]) {
    body.call()
}
}

return this
```

Then consumers of the library could just express the need for a maven pod with docker capabilities by combining the two,
however once again, you will need to express the specific container you wish to execute commands in.
You can **NOT** omit the `node` statement.

Note that `POD_LABEL` will be the innermost generated label to get a node which has all the outer pods available on the
node, as shown in this example:

```groovy
import com.foo.utils.PodTemplates

podTemplates = new PodTemplates()

podTemplates.dockerTemplate {
  podTemplates.mavenTemplate {
    node(POD_LABEL) {
      container('docker') {
        sh "echo hello from $POD_CONTAINER" // displays 'hello from docker'
      }
      container('maven') {
        sh "echo hello from $POD_CONTAINER" // displays 'hello from maven'
      }
     }
  }
}
```

In scripted pipelines, there are cases where this implicit inheritance via nested declaration is not wanted or another
explicit inheritance is preferred.
In this case, use `inheritFrom ''` to remove any inheritance, or `inheritFrom 'otherParent'` to override it.

# Declarative Pipeline

Declarative agents can be defined from yaml

```groovy
pipeline {
  agent {
    kubernetes {
      yaml '''
        apiVersion: v1
        kind: Pod
        metadata:
          labels:
            some-label: some-label-value
        spec:
          containers:
          - name: maven
            image: maven:alpine
            command:
            - cat
            tty: true
          - name: busybox
            image: busybox
            command:
            - cat
            tty: true
        '''
    }
  }
  stages {
    stage('Run maven') {
      steps {
        container('maven') {
          sh 'mvn -version'
        }
        container('busybox') {
          sh '/bin/busybox'
        }
      }
    }
  }
}
```

or using `yamlFile` to keep the pod template in a separate `KubernetesPod.yaml` file

```groovy
pipeline {
  agent {
    kubernetes {
      yamlFile 'KubernetesPod.yaml'
    }
  }
  stages {
    …
  }
}
```

Note that it was previously possible to define `containerTemplate` but that has been deprecated in favor of the yaml format.

```groovy
pipeline {
  agent {
    kubernetes {
      //cloud 'kubernetes'
      containerTemplate {
        name 'maven'
        image 'maven:3.8.1-jdk-8'
        command 'sleep'
        args '99d'
      }
    }
  }
  stages {
    …
  }
}
```

Run steps within a container by default. Steps will be nested within an implicit `container(name) {...}` block instead
of being executed in the jnlp container.

```groovy
pipeline {
  agent {
    kubernetes {
      defaultContainer 'maven'
      yamlFile 'KubernetesPod.yaml'
    }
  }

  stages {
    stage('Run maven') {
      steps {
        sh 'mvn -version'
      }
    }
  }
}
```

Run the Pipeline or individual stage within a custom workspace - not required unless explicitly stated.

```groovy
pipeline {
  agent {
    kubernetes {
      customWorkspace 'some/other/path'
      defaultContainer 'maven'
      yamlFile 'KubernetesPod.yaml'
    }
  }

  stages {
    stage('Run maven') {
      steps {
        sh 'mvn -version'
        sh "echo Workspace dir is ${pwd()}"
      }
    }
  }
}
```

## Default inheritance
Unlike scripted k8s template, declarative templates do not inherit from parent template.
Since the agents declared at stage level can override a global agent, implicit inheritance was leading to confusion.

You need to explicitly declare the inheritance if necessary using the field `inheritFrom`.

In the following example, `nested-pod` will only contain the `maven` container.

```groovy
pipeline {
  agent {
    kubernetes {
      yaml '''
        spec:
        containers:
        - name: golang
            image: golang:1.16.5
            command:
            - sleep
            args:
            - 99d
        '''
    }
  }
  stages {
    stage('Run maven') {
        agent {
            kubernetes {
                yaml '''
                    spec:
                    containers:
                    - name: maven
                      image: maven:3.8.1-jdk-8
                      command:
                      - sleep
                      args:
                      - 99d
                    '''
            }
        }
      steps {
        …
      }
    }
  }
}

```

# Misc.

## Accessing container logs from the pipeline

If you use the `containerTemplate` to run some service in the background
(e.g. a database for your integration tests), you might want to access its log from the pipeline.
This can be done with the `containerLog` step, which prints the log of the
requested container to the build log.

#### Required Parameters
* **name** the name of the container to get logs from, as defined in `podTemplate`. Parameter name
can be omitted in simple usage:

```groovy
containerLog 'mongodb'
```

#### Optional Parameters
* **returnLog** return the log instead of printing it to the build log (default: `false`)
* **tailingLines** only return the last n lines of the log (optional)
* **sinceSeconds** only return the last n seconds of the log (optional)
* **limitBytes** limit output to n bytes (from the beginning of the log, not exact).

Also see the online help and [examples/containerLog.groovy](examples/containerLog.groovy).

# Features controlled using system properties

Please read [Features controlled by system properties](https://www.jenkins.io/doc/book/managing/system-properties/) page to know how to set up system properties within Jenkins.

* `KUBERNETES_JENKINS_URL` : Jenkins URL to be used by agents. This is meant to be used for OEM integration.
* `io.jenkins.plugins.kubernetes.disableNoDelayProvisioning` (since 1.19.1) Whether to disable the no-delay provisioning strategy the plugin uses (defaults to `false`).
* `jenkins.host.address` : (for unit tests) controls the host agents should use to contact Jenkins
* `org.csanchez.jenkins.plugins.kubernetes.PodTemplate.connectionTimeout` : The time in seconds to wait before considering the pod scheduling has failed (defaults to `1000`)
* `org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecDecorator.stdinBufferSize` : stdin buffer size in bytes for commands sent to Kubernetes exec api. A low value will cause slowness in commands executed. A higher value will consume more memory (defaults to `16*1024`)
* `org.csanchez.jenkins.plugins.kubernetes.pipeline.ContainerExecDecorator.websocketConnectionTimeout` : Time to wait for the websocket used by `container` step to connect (defaults to `30`)

# Running on OpenShift

## Random UID problem

OpenShift runs containers using a _random_ UID that is overriding what is specified in Docker images.
For this reason, you may end up with the following warning in your build

```
[WARNING] HOME is set to / in the jnlp container. You may encounter troubles when using tools or ssh client. This usually happens if the uid doesnt have any entry in /etc/passwd. Please add a user to your Dockerfile or set the HOME environment variable to a valid directory in the pod template definition.
```

At the moment the jenkinsci agent image is not built for OpenShift and will issue this warning.

This issue can be circumvented in various ways:
* build a docker image for OpenShift in order to behave when running using an arbitrary uid.
* override HOME environment variable in the pod spec to use `/home/jenkins` and mount a volume to `/home/jenkins` to ensure the user running the container can write to it

See this [example](examples/openshift-home-yaml.groovy) configuration.

## Running with OpenShift 3

OpenShift 3 is based on an older version of Kubernetes, which is not anymore directly supported since Kubernetes plugin version 1.26.0.

To get agents working for Openshift 3, add this `Node Selector` to your Pod Templates:
```
beta.kubernetes.io/os=linux
```

# Windows support

You can run pods on Windows if your cluster has Windows nodes.
See the [example](src/main/resources/org/csanchez/jenkins/plugins/kubernetes/pipeline/samples/windows.groovy).

# Constraints

Multiple containers can be defined in a pod.
One of them is automatically created with name `jnlp`, and runs the Jenkins JNLP agent service, with args `${computer.jnlpmac} ${computer.name}`,
and will be the container acting as Jenkins agent.

Other containers must run a long running process, so the container does not exit. If the default entrypoint or command
just runs something and exit then it should be overridden with something like `cat` with `ttyEnabled: true`.

**WARNING**
If you want to provide your own Docker image for the inbound agent, you **must** name the container `jnlp` so it overrides the default one. Failing to do so will result in two agents trying to concurrently connect to the controller.

# Configuration on minikube

Create and start [minikube](https://github.com/kubernetes/minikube)

The client certificate needs to be converted to PKCS, will need a password

    openssl pkcs12 -export -out ~/.minikube/minikube.pfx -inkey ~/.minikube/apiserver.key -in ~/.minikube/apiserver.crt -certfile ~/.minikube/ca.crt -passout pass:secret

Validate that the certificates work

    curl --cacert ~/.minikube/ca.crt --cert ~/.minikube/minikube.pfx:secret --cert-type P12 https://$(minikube ip):8443

Add a Jenkins credential of type certificate, upload it from `~/.minikube/minikube.pfx`, password `secret`

Fill *Kubernetes server certificate key* with the contents of `~/.minikube/ca.crt`


# Configuration on Google Container Engine

Create a cluster

    gcloud container clusters create jenkins --num-nodes 1 --machine-type g1-small

and note the admin password and server certificate.

Or use Google Developer Console to create a Container Engine cluster, then run

    gcloud container clusters get-credentials jenkins
    kubectl config view --raw

the last command will output kubernetes cluster configuration including API server URL, admin password and root certificate


# Troubleshooting

First watch if the Jenkins agent pods are started.
Make sure you are in the correct cluster and namespace.

    kubectl get -a pods --watch

If they are in a different state than `Running`, use `describe` to get the events

    kubectl describe pods/my-jenkins-agent

If they are `Running`, use `logs` to get the log output

    kubectl logs -f pods/my-jenkins-agent jnlp

If pods are not started or for any other error, check the logs on the controller side.

For more detail, configure a new [Jenkins log recorder](https://wiki.jenkins-ci.org/display/JENKINS/Logging) for
`org.csanchez.jenkins.plugins.kubernetes` at `ALL` level.

To inspect the json messages sent back and forth to the Kubernetes API server you can configure
a new [Jenkins log recorder](https://wiki.jenkins-ci.org/display/JENKINS/Logging) for `okhttp3`
at `DEBUG` level.

## Deleting pods in bad state

    kubectl get pods -o name --selector=jenkins=slave --all-namespaces  | xargs -I {} kubectl delete {}

## Pipeline `sh` step hangs when multiple containers are used
To debug this you need to set `-Dorg.jenkinsci.plugins.durabletask.BourneShellScript.LAUNCH_DIAGNOSTICS=true` system property
and then restart the pipeline. Most likely in the console log you will see the following:
```console
sh: can't create /home/jenkins/agent/workspace/thejob@tmp/durable-e0b7cd27/jenkins-log.txt: Permission denied
sh: can't create /home/jenkins/agent/workspace/thejob@tmp/durable-e0b7cd27/jenkins-result.txt.tmp: Permission denied
mv: can't rename '/home/jenkins/agent/workspace/thejob@tmp/durable-e0b7cd27/jenkins-result.txt.tmp': No such file or directory
touch: /home/jenkins/agent/workspace/thejob@tmp/durable-e0b7cd27/jenkins-log.txt: Permission denied
touch: /home/jenkins/agent/workspace/thejob@tmp/durable-e0b7cd27/jenkins-log.txt: Permission denied
touch: /home/jenkins/agent/workspace/thejob@tmp/durable-e0b7cd27/jenkins-log.txt: Permission denied
```
Usually this happens when UID of the user in `jnlp` container differs from the one in another container(s). 
All containers you use should have the same UID of the user, also this can be achieved by setting `securityContext`:
```yaml
apiVersion: v1
kind: Pod
spec:
  securityContext:
    runAsUser: 1000 # default UID of jenkins user in agent image
  containers:
  - name: maven
    image: maven:3.8.1-jdk-8
    command:
    - cat
    tty: true
```

## Using WebSockets with a Jenkins controller with self-signed HTTPS certificate

Using WebSockets is the easiest and recommended way to establish the connection between agents and a Jenkins controller running outside the cluster.
However, if your Jenkins controller has HTTPS configured with self-signed certificate, you'll need to make sure the agent container trusts the CA.
To do that, you can extend the `jenkins/inbound-agent` image and add your certificate as follows:

```Dockerfile
FROM jenkins/inbound-agent:jdk8

USER root

ADD cert.pem /tmp/cert.pem

RUN keytool -noprompt -storepass changeit \
  -keystore "$JAVA_HOME/jre/lib/security/cacerts" \
  -import -file /tmp/cert.pem -alias jenkinsMaster && \
  rm -f /tmp/cert.pem

USER jenkins
```

Or, if you are using JDK 11:

```Dockerfile
FROM jenkins/inbound-agent:jdk11

USER root

ADD cert.pem /tmp/cert.pem

RUN keytool -noprompt -storepass changeit -cacerts \
  -import -file /tmp/cert.pem -alias jenkinsMaster && \
  rm -f /tmp/cert.pem

USER jenkins
```

Then, use it as the `jnlp` container for the pod template as usual. No command or args need to be specified.

> **Notes:**
>
> * Support for using WebSockets with JDK 11 was added in the Remoting v4.11, so make sure your base image is new enough. See [here](https://issues.jenkins.io/browse/JENKINS-61212) for more information.
>
> * When using the WebSocket mode, the `-disableHttpsCertValidation` on the `jenkins/inbound-agent` becomes unavailable, as well as `-cert`, and that's why you have to extend the docker image.

## [WARNING] label option is deprecated

```
[WARNING] label option is deprecated. To use a static pod template, use the 'inheritFrom' option.
```

You need to change from something like:

```
agent {
	kubernetes {
		label 'somelabel'
	}
}
```

To something like:

```
agent {
	kubernetes {
		inheritFrom 'somelabel'
	}
}
```


# Building and Testing

Integration tests will use the currently configured context auto-detected from kube config file or service account.

## Manual Testing

Run `mvn clean install` and copy `target/kubernetes.hpi` to Jenkins plugins folder.

## Running Kubernetes Integration Tests

Please note that the system you run `mvn` on needs to be reachable from the cluster.
If you see the agents happen to connect to the wrong host, see you can use
`jenkins.host.address` as mentioned above.

### Integration Tests with Minikube

For integration tests install and start [minikube](https://github.com/kubernetes/minikube).
Tests will detect it and run a set of integration tests in a new namespace.

Some integration tests run a local jenkins, so the host that runs them needs
to be accessible from the kubernetes cluster.
By default Jenkins will listen on `192.168.64.1` interface only, for security reasons.
If your minikube is not running in that network, pass `connectorHost` to maven, ie.

    mvn clean install -DconnectorHost=$(minikube ip | sed -e 's/\([0-9]*\.[0-9]*\.[0-9]*\).*/\1.1/')

If you don't mind others in your network being able to use your test jenkins you could just use this:

    mvn clean install -DconnectorHost=0.0.0.0

Then your test jenkins will listen on all ip addresses so that the build pods will be able to connect from the pods in your minikube VM to your host.  

If your minikube is running in a VM (e.g. on virtualbox) and the host running `mvn`
does not have a public hostname for the VM to access, you can set the `jenkins.host.address`
system property to the (host-only or NAT) IP of your host:

    mvn clean install -Djenkins.host.address=192.168.99.1

### Integration Tests with Microk8s

If [Microk8s](https://microk8s.io/) is running and is the default context in your `~/.kube/config`,
just run as

    mvn clean install -Pmicrok8s

This assumes that from a pod, the host system is accessible as IP address `10.1.1.1`.
It might be some variant such as `10.1.37.1`,
in which case you would need to set `-DconnectorHost=… -Djenkins.host.address=…` instead.
To see the actual address, try:

```bash
ifdata -pa cni0
```

Or to verify the networking inside a pod:

```bash
kubectl run --rm --image=praqma/network-multitool --restart=Never --attach sh ip route | fgrep 'default via'
```

### Integration Tests in a Different Cluster

Try

```bash
bash test-in-k8s.sh
```

# Docker image

Docker image for Jenkins, with plugin installed.
Based on the [official image](https://hub.docker.com/r/jenkins/jenkins/).

## Running the Docker image

```bash
docker run --rm --name jenkins -p 8080:8080 -p 50000:50000 -v /var/jenkins_home csanchez/jenkins-kubernetes
```


# Running in Kubernetes

The example configuration will create a stateful set running Jenkins with persistent volume
and using a service account to authenticate to Kubernetes API.

## Running locally with minikube

A local testing cluster with one node can be created with [minikube](https://github.com/kubernetes/minikube)

```bash
minikube start
```

You may need to set the correct permissions for host mounted volumes

```bash
minikube ssh
sudo chown 1000:1000 /tmp/hostpath-provisioner/pvc-*
```

Then create the Jenkins namespace, controller and Service with

```bash
kubectl create namespace kubernetes-plugin
kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin
kubectl create -f src/main/kubernetes/service-account.yml
kubectl create -f src/main/kubernetes/jenkins.yml
```

Get the url to connect to with

```bash
minikube service jenkins --namespace kubernetes-plugin --url
```

## Running in Google Container Engine GKE

Assuming you created a Kubernetes cluster named `jenkins` this is how to run both Jenkins and agents there.

Creating all the elements and setting the default namespace

```bash
kubectl create namespace kubernetes-plugin
kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin
kubectl create -f src/main/kubernetes/service-account.yml
kubectl create -f src/main/kubernetes/jenkins.yml
```

Connect to the ip of the network load balancer created by Kubernetes, port 80.
Get the ip (in this case `104.197.19.100`) with `kubectl describe services/jenkins`
(it may take a bit to populate)

    $ kubectl describe services/jenkins
    Name:           jenkins
    Namespace:      default
    Labels:         <none>
    Selector:       name=jenkins
    Type:           LoadBalancer
    IP:         10.175.244.232
    LoadBalancer Ingress:   104.197.19.100
    Port:           http    80/TCP
    NodePort:       http    30080/TCP
    Endpoints:      10.172.1.5:8080
    Port:           agent   50000/TCP
    NodePort:       agent   32081/TCP
    Endpoints:      10.172.1.5:50000
    Session Affinity:   None
    No events.

Until Kubernetes 1.4 removes the SNATing of source ips, seems that CSRF (enabled by default in Jenkins 2)
needs to be configured to avoid `WARNING: No valid crumb was included in request` errors.
This can be done checking _Enable proxy compatibility_ under Manage Jenkins -> Configure Global Security

Configure Jenkins, adding the `Kubernetes` cloud under configuration, setting
Kubernetes URL to the container engine cluster endpoint or simply `https://kubernetes.default.svc.cluster.local`.
Under credentials, click `Add` and select `Kubernetes Service Account`,
or alternatively use the Kubernetes API username and password. Select 'Certificate' as credentials type if the
kubernetes cluster is configured to use client certificates for authentication.

Using `Kubernetes Service Account` will cause the plugin to use the default token mounted inside the Jenkins pod. See [Configure Service Accounts for Pods](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/) for more information.

![image](credentials.png)

You may want to set `Jenkins URL` to the internal service IP, `http://10.175.244.232` in this case,
to connect through the internal network.

Set `Container Cap` to a reasonable number for tests, i.e. 3.

Add an image with

* Docker image: `jenkins/inbound-agent`
* Jenkins agent root directory: `/home/jenkins/agent`

![image](configuration.png)

Now it is ready to be used.

Tearing it down

```bash
kubectl delete namespace/kubernetes-plugin
```


## Customizing the deployment

### Modify CPUs and memory request/limits (Kubernetes Resource API)

Modify file `./src/main/kubernetes/jenkins.yml` with desired limits

```yaml
resources:
  limits:
    cpu: 1
    memory: 1Gi
  requests:
    cpu: 0.5
    memory: 500Mi
```

Note: the JVM will use the memory `requests` as the heap limit (-Xmx)

## Building

```bash
docker build -t csanchez/jenkins-kubernetes .
```
 
# Related Projects

* [Kubernetes Pipeline plugin](https://github.com/jenkinsci/kubernetes-pipeline-plugin): pipeline extension to provide native support for using Kubernetes pods, secrets and volumes to perform builds
* [kubernetes-credentials](https://github.com/jenkinsci/kubernetes-credentials-plugin): Credentials provider that reads Kubernetes secrets
