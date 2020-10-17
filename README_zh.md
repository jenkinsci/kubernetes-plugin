jenkins-kubernetes-plugin
=========================

这是一个支持在 Kubernetes 集群上运行动态节点的 Jenkins 插件。

Based on the [Scaling Docker with Kubernetes](http://www.infoq.com/articles/scaling-docker-with-kubernetes) article,
automates the scaling of Jenkins agents running in Kubernetes.

该插件在节点启动时创建一个包含 Docker 镜像的 Kubernetes Pod，并在构建结束时停止。

For that some environment variables are automatically injected:
节点使用 JNLP 来启动，并自动连接到 Jenkins master 上。期间，会自动注入一些环境变量：

* `JENKINS_URL`: Jenkins 界面地址
* `JENKINS_SECRET`: 认证用的密钥
* `JENKINS_AGENT_NAME`: Jenkins 节点的名称
* `JENKINS_NAME`: Jenkins 节点的名称（已废弃，保持向后兼容）

使用镜像 [`jenkins/inbound-agent`](https://hub.docker.com/r/jenkins/inbound-agent) 做的测试。
查看[Docker 镜像源码](https://github.com/jenkinsci/docker-inbound-slave)。

Jenkins master 可以不在 Kubernetes 上运行。

# Kubernetes 云配置

在 Jenkins 设置中，点击"添加云"，选择 `Kubernetes` 后填写如下信息：
_名称_、_Kubernetes 地址_、 _Kubernetes server 服务证书 key_ 等等。

如果没有设置 _Kubernetes 地址_，连接选项会自动从 `Service Account` 或 `kube config` 文件中读取。

在 Kubernetes 外运行的 Jenkins master 话，则需要设置凭据。凭据的值就是你为 Jenkins 在集群中创建
的 `Service Account` 的 `Token`，节点运行时也会使用。

### 限制哪些任务可以使用你配置的云

"云"可以配置为只运行特定的任务来使用。

要使用该特性的话，在你的云的高级配置中勾选 `Restrict pipeline support to authorized folders` 。
对要使用该云配置的任务，需要在对应的文件夹配置中添加。

# 流水线支持

在可以流水线中定义节点并使用，然而，默认的执行环境总会是 `jnlp` 容器。你需要根据情况指定容器。

这会运行在 `jnlp` 容器中

```groovy
// this guarantees the node will use this template
def label = "mypod-${UUID.randomUUID().toString()}"
podTemplate(label: label) {
    node(label) {
        stage('Run shell') {
            sh 'echo hello world'
        }
    }
}
```

这会运行在指定的容器中

```groovy
def label = "mypod-${UUID.randomUUID().toString()}"
podTemplate(label: label) {
  node(label) {
    stage('Run shell') {
      container('mycontainer') {
        sh 'echo hello world'
      }
    }
  }
}
```

在[examples 目录](examples)中可以找到更多的例子。

可以在模板中自定义 `jnlp` 节点镜像

```groovy
containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:4.3-4-alpine', args: '${computer.jnlpmac} ${computer.name}'),
```

或者使用 `yaml` 语法

```yaml
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: 'jenkins/inbound-agent:4.3-4-alpine'
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
```

### 容器组支持

可以在 `agent pod` 中定义多个容器，并共享类似挂载点的资源。每个容器中的端口都可以通过 `localhost` 访问。

通过 `container` 指令可以直接在每个容器中执行命令。该特性仍然还有一些并发执行和流水线恢复的问题，因此还处于 **ALPHA** 状态。

```groovy
def label = "mypod-${UUID.randomUUID().toString()}"
podTemplate(label: label, containers: [
    containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat'),
    containerTemplate(name: 'golang', image: 'golang:1.8.0', ttyEnabled: true, command: 'cat')
  ]) {

    node(label) {
        stage('Get a Maven project') {
            git 'https://github.com/jenkinsci/kubernetes-plugin.git'
            container('maven') {
                stage('Build a Maven project') {
                    sh 'mvn -B clean install'
                }
            }
        }

        stage('Get a Golang project') {
            git url: 'https://github.com/hashicorp/terraform.git'
            container('golang') {
                stage('Build a Go project') {
                    sh """
                    mkdir -p /go/src/github.com/hashicorp
                    ln -s `pwd` /go/src/github.com/hashicorp/terraform
                    cd /go/src/github.com/hashicorp/terraform && make core-dev
                    """
                }
            }
        }

    }
}
```

### Pod 和容器模板配置

`podTemplate` 是 `pod` 的一个模板，用于创建节点。它可以通过用户界面或者流水线来配置。
不管哪种方式，都提供了如下的字段：

* **cloud** 在 Jenkins 设置中配置的云的名称。默认为 `kubernetes`
* **name** pod 的名称
* **namespace** pod 的命名空间
* **label** pod 的标签。为了避免在多个构建中冲突，最好设置一个唯一值
* **yaml** [Pod 的 yaml 形式](https://kubernetes.io/docs/reference/generated/kubernetes-api/v1.10/#pod-v1-core)允许设置任何值（即使当前字段列表中不存在的）
* **containers** 容器模板，用于创建 pod 中的容器 *(如下所示)*.
* **serviceAccount** pod 的 service account
* **nodeSelector** node 的节点选择器
* **nodeUsageMode** "正常"或者"独占"，这会决定 Jenkins 是否只调度与标签表达式匹配的任务或尽可能地使用节点
* **volumes** 定义在 pod 中的卷，并会挂载到 **所有** 容器
* **envVars** 环境变量，会应用到 **所有** 容器
    * **envVar** 直接定义的环境变量
    * **secretEnvVar** 值来自于 Kubernetes 中的 secret
* **imagePullSecrets** [从私有 Docker registry 上拉取镜像](https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/)时使用的凭据名称列表
* **annotations** 应用到 pod 上的注解
* **inheritFrom** 要继承的一个或多个 pod 模板 *(详情参见下文)*.
* **slaveConnectTimeout** 节点上线时的请求超时时间（秒）*(详情参见下文)*.
* **podRetention** 用于决定是否保留节点 pod。可以是 'never()'、 'onFailure()'、 'always()' 或 'default()'，如果为空的话，超过 `activeDeadlineSeconds` 设定的时间时间后会删除 pod
* **activeDeadlineSeconds** 如果 `podRetention` 设置为 'never()' 或 'onFailure()' 的话，在时间超过后 pod 会被删除
* **idleMinutes** 允许 pod 保持活跃以便再次使用，直到最后一次执行后的时间超过配置的分钟数

`containerTemplate` 是容器的模板，会被加到 pod 中。同样地，它的配置可以通过用户界面或流水线来配置，字段包括：

* **name** 容器的名称
* **image** 容器的镜像
* **envVars** 应用于容器的环境变量 **(是 pod 级别环境变量的补充，并会覆盖)**.
    * **envVar** 直接定义的环境变量
    * **secretEnvVar** 值来自于 Kubernetes 中的 secret
* **command** 容器启动时会执行的命令
* **args** 传递给启动命令的参数
* **ttyEnabled** 是否启用 `tty` 的标记
* **livenessProbe** 添加给容器中的可执行 liveness 探测的参数（不支持 httpGet liveness 探测）
* **ports** 容器中暴露的端口

#### 指定不同的节点连接超时时间

节点的连接超时时间默认为100秒。在某些情况下，你可能想要修改这个值，这时你可以设置系统属性 
`org.csanchez.jenkins.plugins.kubernetes.PodTemplate.connectionTimeout` 为不同的值。
请阅读[由系统属性控制的特性](https://wiki.jenkins.io/display/JENKINS/Features+controlled+by+system+properties)，以便
了解如何在 Jenkins 中设置系统属性。

#### 使用 yaml 定义 Pod 模板

为了支持 Kubernetes 中 `Pod` 对象的任何可能的值，我们可以传递一个 yaml 片段，作为模板的基础。任何在 yaml 外部设置的属性的优先级更高。

```groovy
def label = "mypod-${UUID.randomUUID().toString()}"
podTemplate(label: label, yaml: """
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
    - cat
    tty: true
"""
) {
    node (label) {
      container('busybox') {
        sh "hostname"
      }
    }
}
```

你可以使用步骤 [`readFile`](https://jenkins.io/zh/doc/pipeline/steps/workflow-basic-steps/#code-readfile-code-read-file-from-workspace) 
或 [`readTrusted`](https://jenkins.io/doc/pipeline/steps/coding-webhook/#readtrusted-read-trusted-file-from-scm) 从文件中年加载 yaml.
它从 Jenkins 控制台的插件配置界面上也能访问到。

#### 存活性探测的使用

```groovy
containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: 'cat',
    livenessProbe: containerLivenessProbe( execArgs: 'some --command',
    initialDelaySeconds: 30, timeoutSeconds: 1, failureThreshold: 3, periodSeconds: 10, successThreshold: 1))
```

查看页面[定义一个存活性命令](https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-probes/#defining-a-liveness-command) 了解更多详情。

### Pod 模板继承

`podTemplate` 可以继承一个已有的模板，或者不继承。这意味着，`podTemplate` 将会继承节点选择器、service account、镜像拉取凭据，容器模板和卷。

**yaml** 是 **永远** 不会合并的，如果在子 pod 模板中定义了的话，就不会用父模板的。

**Service account** 和 **节点选择器** 会覆盖父模板中的值。

**容器模板** 如果匹配到了父模板中名称相同到容器模板的话，则会继承父的配置；如果没有匹配到，就用当前的。

**卷** 继承机制与 **容器模板** 一致。

**镜像拉取凭据** 所有定义在"父"和"当前"模板中的凭据都会用到。

在下面例子中，会继承之前创建的 pod 模板，并只是覆盖 `maven` 的版本，使用 jdk-7 :

```groovy
podTemplate(label: 'anotherpod', inheritFrom: 'mypod',  containers: [
    containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-7-alpine')
  ]) {

      //Let's not repeat ourselves and ommit this part
}
```

注意，我们只需要指定不同的部分。因此，不需要 `ttyEnabled` 和 `command`，它们都会被继承。同样地，由于父模板中有 `golang` 容器，所以也会被添加。

#### Pod 模板多重继承

字段 `inheritFrom` 可以引用单个或由空格分割的多个模板。在包含多个的情况下，会按照模板出现的顺序依此处理 *(后面的覆盖先出现的)*。
如果被引用的模板没有被找到的话，就会被忽略。

#### Pod 模板嵌套

字段 `inheritFrom` 提供了一个方便的方法来组合已有的 podTemplate。很多情况下，直接在流水线中使用 groovy 来定义和组合 podTemplate 会很有用。
嵌套的方式使之变的可能。你可以把多个 podTemplate 嵌套起来，组合成一个。

下面的例子中，把两个不同的 podTemplate 组合起来，变成一个具有 maven 和 docker 能力的模板。

```groovy
podTemplate(label: 'docker', containers: [containerTemplate(image: 'docker', name: 'docker', command: 'cat', ttyEnabled: true)]) {
    podTemplate(label: 'maven', containers: [containerTemplate(image: 'maven', name: 'maven', command: 'cat', ttyEnabled: true)]) {
        // do stuff
    }
}
```

这个特性非常有用，流水线库的开发人员可以把 podTemplate 包装成函数，然后让用户根据实际需要来调用这些函数。

例如，为 podTemplate 创建一个函数，并导入后使用。我们的文件位于 `src/com/foo/utils/PodTemplates.groovy`:

```groovy
package com.foo.utils

public void dockerTemplate(body) {
  def label = "worker-${UUID.randomUUID().toString()}"
  podTemplate(label: label,
        containers: [containerTemplate(name: 'docker', image: 'docker', command: 'cat', ttyEnabled: true)],
        volumes: [hostPathVolume(hostPath: '/var/run/docker.sock', mountPath: '/var/run/docker.sock')]) {
    body.call(label)
  }
}

public void mavenTemplate(body) {
  def label = "worker-${UUID.randomUUID().toString()}"
  podTemplate(label: label,
        containers: [containerTemplate(name: 'maven', image: 'maven', command: 'cat', ttyEnabled: true)],
        volumes: [secretVolume(secretName: 'maven-settings', mountPath: '/root/.m2'),
                  persistentVolumeClaim(claimName: 'maven-local-repo', mountPath: '/root/.m2nrepo')]) {
    body.call(label)
  }
}

return this
```

该库的使用者只需要把两个函数组合起来就可以同时满足对 maven 和 docker 的需求。然而，你需要指定命令执行时所用的容器。另外，你 **不** 可以忽略 `node` 语句。

注意，你 **必须** 使用最里层生成的标签来获取节点，在该节点上包含所有的外部 pod，如下所示：

```groovy
import com.foo.utils.PodTemplates

slaveTemplates = new PodTemplates()

slaveTemplates.dockerTemplate {
  slaveTemplates.mavenTemplate { label ->
    node(label) {
      container('docker') {
        sh 'echo hello from docker'
      }
      container('maven') {
        sh 'echo hello from maven'
      }
     }
  }
}
```

#### 使用不同的命名空间

有些情况，你需要让节点运行在一个不同的命名空间中，而不是定义在"云"配置中的那个。例如：为了测试你可能需要让节点运行在命名空间 `ephemeral` 中。
这些情况下，你可以通过界面或者流水线来指定一个命名空间。

#### 设置不同 `shell` 命令替换 `/bin/sh`

默认情况下， `shell` 命令是 `/bin/sh`。在一些情况下，你可能想使用例如 `/bin/bash` 这样其他的命令。

```groovy
podTemplate(label: my-label) {
  node(my-label) {
    stage('Run specific shell') {
      container(name:'mycontainer', shell:'/bin/bash') {
        sh 'echo hello world'
      }
    }
  }
}
```

## 容器配置

在流水线中的 podTemplate 里配置一个容器时，有如下的可选项：

```groovy
podTemplate(label: 'mypod', cloud: 'kubernetes', containers: [
    containerTemplate(
        name: 'mariadb',
        image: 'mariadb:10.1',
        ttyEnabled: true,
        privileged: false,
        alwaysPullImage: false,
        workingDir: '/home/jenkins',
        resourceRequestCpu: '50m',
        resourceLimitCpu: '100m',
        resourceRequestMemory: '100Mi',
        resourceLimitMemory: '200Mi',
        envVars: [
            envVar(key: 'MYSQL_ALLOW_EMPTY_PASSWORD', value: 'true'),
            secretEnvVar(key: 'MYSQL_PASSWORD', secretName: 'mysql-secret', secretKey: 'password'),
            ...
        ],
        ports: [portMapping(name: 'mysql', containerPort: 3306, hostPort: 3306)]
    ),
    ...
],
volumes: [
    emptyDirVolume(mountPath: '/etc/mount1', memory: false),
    secretVolume(mountPath: '/etc/mount2', secretName: 'my-secret'),
    configMapVolume(mountPath: '/etc/mount3', configMapName: 'my-config'),
    hostPathVolume(mountPath: '/etc/mount4', hostPath: '/mnt/my-mount'),
    nfsVolume(mountPath: '/etc/mount5', serverAddress: '127.0.0.1', serverPath: '/', readOnly: true),
    persistentVolumeClaim(mountPath: '/etc/mount6', claimName: 'myClaim', readOnly: true)
],
imagePullSecrets: [ 'pull-secret' ],
annotations: [
    podAnnotation(key: "my-key", value: "my-value")
    ...
]) {
   ...
}
```

## 申明式流水线

Jenkins 2.66+ 开始支持申明式流水线。申明式的节点可以用 yaml 的方式来定义：

```groovy
pipeline {
  agent {
    kubernetes {
      label 'mypod'
      yaml """
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
"""
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

或者，使用 `yamlFile` 让 pod 模板写在一个单独的文件 `KubernetesPod.yaml` 里。

```groovy
pipeline {
  agent {
    kubernetes {
      label 'mypod'
      yamlFile 'KubernetesPod.yaml'
    }
  }
  stages {
      //...
  }
}
```

注意，之前是可以定义 `containerTemplate` ，但目前已经在 yaml 格式中被弃用。

```groovy
pipeline {
  agent {
    kubernetes {
      //cloud 'kubernetes'
      label 'mypod'
      containerTemplate {
        name 'maven'
        image 'maven:3.3.9-jdk-8-alpine'
        ttyEnabled true
        command 'cat'
      }
    }
  }
  stages { ... }
}
```

在自定义的工作空间里运行流水线或者单个阶段（stage）——除非明确说明，否则是不需要的。

```groovy
pipeline {
  agent {
    kubernetes {
      label 'mypod'
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

## 从流水线中访问容器日志

如果你使用 `containerTemplate` 在后台运行一些服务（例如：集成测试用的数据库），你可能想在流水线中访问它的日志。
这可以通过 `containerLog` 这个步骤（step）来完成，它会打印你所请求的容器的日志到构建日志中。

#### 必要参数

* **名称** 要获取日志的容器的名称，定义在 `podTemplate` 中。简单使用的话，可以忽略参数名：

```groovy
containerLog 'mongodb'
```

#### 可选参数

* **returnLog** 返回而不是打印到构建日志中（默认值：`false`）
* **tailingLines** 只返回最后几行的日志（可选）
* **sinceSeconds** 只返回最后几秒的日志（可选）
* **limitBytes** 限制输出的字节数（从日志的开头算起，不准确）

参照在线帮助和 [examples/containerLog.groovy](examples/containerLog.groovy).

# 约束

在 pod 中可以定义多个容器。其中的名称为 `jnlp` 的容器是自动创建的，会作为 Jenkins JNLP 节点服务，并带有参数 `${computer.jnlpmac} ${computer.name}`。

其他容器必须有一直运行的进程，这样容器才不会退出。如果默认的 entrypoint 或命令运行后就退出的话，它应该使用 `cat` 以及 `ttyEnabled: true` 来覆盖。

**警告**

如果你要为 JNLP 节点提供自己的 Docker 镜像，你 **必须** 把容器命名为 `jnlp`，才会覆盖默认的。如果不这么做的话，就会导致有两个节点同时去连接 maser.

# 覆盖预定义参数

默认情况下，Jenkins 在调用节点时会比较保守。也就是说，如果队列中有两个构建任务，它不会立刻调度两个执行节点。
By default, Jenkins spawns agents conservatively. Say, if there are 2 builds in queue, it won't spawn 2 executors immediately.
It will spawn one executor and wait for sometime for the first executor to be freed before deciding to spawn the second executor.
Jenkins makes sure every executor it spawns is utilized to the maximum.
If you want to override this behaviour and spawn an executor for each build in queue immediately without waiting,
you can use these flags during Jenkins startup:

```
-Dhudson.slaves.NodeProvisioner.initialDelay=0
-Dhudson.slaves.NodeProvisioner.MARGIN=50
-Dhudson.slaves.NodeProvisioner.MARGIN0=0.85
```

# 在 minikube 上配置

创建并启动 [minikube](https://github.com/kubernetes/minikube)

客户端证书需要转换为 PKCS，并需要密码

    openssl pkcs12 -export -out ~/.minikube/minikube.pfx -inkey ~/.minikube/apiserver.key -in ~/.minikube/apiserver.crt -certfile ~/.minikube/ca.crt -passout pass:secret

然后，验证证书可用

    curl --cacert ~/.minikube/ca.crt --cert ~/.minikube/minikube.pfx:secret --cert-type P12 https://$(minikube ip):8443

增加一个证书类型的 Jenkins 凭据，从 `~/.minikube/minikube.pfx` 上传，密码为 `secret`

用文件 `~/.minikube/ca.crt` 的内容来填写 *Kubernetes 服务证书 key*

# 在 Google Container Engine 上配置

创建一个集群

    `gcloud container clusters create jenkins --num-nodes 1 --machine-type g1-small`

并记下管理员密码和服务证书。

或只是用 Google 开发者控制台来创建一个容器引擎集群，然后运行如下命令

    gcloud container clusters get-credentials jenkins
    kubectl config view --raw

最后的命令会输出 kubernetes 集群配置，包括：API 服务地址、管理员密码和根证书。

# 调试

首先，确保你在正确的集群和命名空间中，观察 Jenkins 节点是否启动。

    kubectl get -a pods --watch

如果他们的状态不是 `Running`，使用 `describe` 获取事件信息

    kubectl describe pods/my-jenkins-agent

如果他们处于 `Running`，使用 `logs` 获取输出的日志

    kubectl logs -f pods/my-jenkins-agent jnlp

如果 pod 还没有启动，或没有其他任何错误，请检查 master 上的日志。

想要更多的细节的话，配置一个新的 [Jenkins 日志记录器](https://wiki.jenkins-ci.org/display/JENKINS/Logging)，
设置 `org.csanchez.jenkins.plugins.kubernetes` 的级别为 `ALL`.

为了检查发送到 Kubernetes API 服务的 json 数据，你可以配置一个新的 [Jenkins 日志记录器](https://wiki.jenkins-ci.org/display/JENKINS/Logging)，
设置 `okhttp3` 的级别为 `ALL`.

## 删除错误状态的 pod

    kubectl get pods -o name --selector=jenkins=slave --all-namespaces  | xargs -I {} kubectl delete {}

# 构建与测试

集成测试会使用从 kube config 文件或 service account 中自动检测到的配置。

## 手工测试

运行 `mvn clean install` 并拷贝 `target/kubernetes.hpi` 到 Jenkins 的插件目录中。

## 在 Kubernetes 中运行集成测试

请注意，你运行 `mvn` 的系统需要能够从集群访问到。如果你看到节点连接了错误的主机，那么，你可以使用上面提到的 `jenkins.host.address` 来替代。

### 在 Minikube 中运行集成测试

为了集成测试，需要安装和启动 [minikube](https://github.com/kubernetes/minikube).测试程序会检测并在一个新的命名空间中运行。

有些集成测试运行一个本地 Jenkins，因此，你运行的主机需要能从 Kubernetes 集群访问到。默认情况下，处于安全的考虑，Jenkins 只会监听 `192.168.64.1`。
如果你的 minikube 没有运行在那个网络上，可以给 maven 传递参数 `connectorHost`.

    mvn clean install -DconnectorHost=$(minikube ip | sed -e 's/\([0-9]*\.[0-9]*\.[0-9]*\).*/\1.1/')

如果你不在意你的网络中其他人也可以使用你测试的 Jenkins，那么，你可以这么使用：

    mvn clean install -DconnectorHost=0.0.0.0

然后，你测试用的 Jenkins 将会监听所有的 ip 地址。因此，你构建用的 pod 可以容你的 minikube 虚拟机中访问到你的主机。

如果你的 minikube 是运行在一个虚拟机中（例如：virtualbox），而且运行 `mvn` 的主机没有一个公共的主机名可以让虚拟机访问到，你可以设置
系统属性 `jenkins.host.address` 为你的主机（主机模式或 NAT）的 IP：

    mvn clean install -Djenkins.host.address=192.168.99.1

### 在不同的集群中运行集成测试

确保你使用下面的命令创建命名空间和角色，然后，在命名空间 `kubernetes-plugin` 以及 service account `jenkins`
(要使用不同的命名空间的话，请编辑 `src/test/kubernetes/service-account.yml` 文件)下运行

```
kubectl create namespace kubernetes-plugin-test
kubectl create namespace kubernetes-plugin-test-overridden-namespace
kubectl create namespace kubernetes-plugin-test-overridden-namespace2
kubectl apply -n kubernetes-plugin-test -f src/main/kubernetes/service-account.yml
kubectl apply -n kubernetes-plugin-test-overridden-namespace -f src/main/kubernetes/service-account.yml
kubectl apply -n kubernetes-plugin-test-overridden-namespace2 -f src/main/kubernetes/service-account.yml
kubectl apply -n kubernetes-plugin-test -f src/test/kubernetes/service-account.yml
kubectl apply -n kubernetes-plugin-test-overridden-namespace -f src/test/kubernetes/service-account.yml
kubectl apply -n kubernetes-plugin-test-overridden-namespace2 -f src/test/kubernetes/service-account.yml
```

# Docker 镜像

Jenkins 的 Docker 镜像，并安装好插件。基于[官方镜像](https://registry.hub.docker.com/_/jenkins/)。

## 运行 Docker 镜像

    docker run --rm --name jenkins -p 8080:8080 -p 50000:50000 -v /var/jenkins_home csanchez/jenkins-kubernetes

# 在 Kubernetes 中运行

示例的配置会创建一个带有持久卷的 stateful set 运行 Jenkins，并使用一个 service account 来通过 Kubernetes API 的认证。

## 在本地的 minikube 中运行

使用 [minikube](https://github.com/kubernetes/minikube) 可以创建只有一个节点的本地测试集群

    minikube start

你可能需要为主机挂载卷设置正确的权限

    minikube ssh
    sudo chown 1000:1000 /tmp/hostpath-provisioner/pvc-*

然后，使用如下命令创建 Jenkins 的命名空间，controller 以及 service

    kubectl create namespace kubernetes-plugin
    kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin
    kubectl create -f src/main/kubernetes/service-account.yml
    kubectl create -f src/main/kubernetes/jenkins.yml

通过下面的命令获取连接用的地址

    minikube service jenkins --namespace kubernetes-plugin --url

## 在 Google Container Engine GKE 中运行

假设，你已经创建来一个名为 `jenkins` 的 Kubernetes 集群，用来运行 Jenkins 以及节点。

创建所有的元素，并设置默认的命名空间

    kubectl create namespace kubernetes-plugin
    kubectl config set-context $(kubectl config current-context) --namespace=kubernetes-plugin
    kubectl create -f src/main/kubernetes/service-account.yml
    kubectl create -f src/main/kubernetes/jenkins.yml

连接由 Kubernetes 创建的网络负载均衡的 ip，端口为 80。使用命令 `kubectl describe services/jenkins` （这可能需要点时间）获取 ip（在这里是 `104.197.19.100`）

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

This can be done checking _Enable proxy compatibility_ under Manage Jenkins -> Configure Global Security
直到 Kubernetes 1.4 移除了源 ip 的 SNAT，似乎需要配置 CSRF(在 Jenkins 2 中默认启用) 以避免 `WARNING: No valid crumb was included in request` 错误。
可以在"管理 Jenkins" -> "全局安全配置"下的 _启用代理兼容性_。

配置 Jenkins，在配置中添加 `Kubernetes` 云，设置容器引擎集群的 Kubernetes 地址，或只是简单的为 `https://kubernetes.default.svc.cluster.local`.
在凭据位置，点击`添加`并选择 `Kubernetes Service Account`,或使用 Kubernetes API 的用户名和密码。
或者集群配置为使用客户端证书来认证的话，选择`证书`作为凭据的类型。

使用 `Kubernetes Service Account` 会导致插件使用挂载到 Jenkins pod 中的默认 token。 
想要了解更多详情的话，请查看 [为 pod 配置 Service Accounts](https://kubernetes.io/docs/tasks/configure-pod-container/configure-service-account/)。

![image](credentials.png)

你可能想把 `Jenkins URL` 设置为内部的服务 IP，通过内部网络来连接。当前示例中是 `http://10.175.244.232`。

为测试设置合理的值到 `Container Cap`，例如：3。

使用下面的方式添加一个镜像：

* Docker 镜像：`jenkins/inbound-agent`
* Jenkins 节点根目录：`/home/jenkins`

![image](configuration.png)

现在，已经可以开始使用了。

要停止的话，请使用命令：

    kubectl delete namespace/kubernetes-plugin

## 自定义 deployment

### 修改 CPU 和内存请求或限制 (Kubernetes 资源 API)

修改文件 `./src/main/kubernetes/jenkins.yml` 为所需要的限制

```yaml
resources:
      limits:
        cpu: 1
        memory: 1Gi
      requests:
        cpu: 0.5
        memory: 500Mi
```

注意：虚拟机会使用 `requests` 的内存作为堆的限制(-Xmx)

## 构建

    docker build -t csanchez/jenkins-kubernetes .

# 相关项目

* [Kubernetes Pipeline plugin](https://github.com/jenkinsci/kubernetes-pipeline-plugin): 流水线扩展，为构建提供了对 Kubernetes pods, secrets 和卷的直接支持
* [kubernetes-credentials](https://github.com/jenkinsci/kubernetes-credentials-plugin): 凭据提供者，用于读取 Kubernetes secrets
