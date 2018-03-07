CHANGELOG
=========

1.3.2
-----
* Fix ssh-agent execution inside container. envVars on procstarter were discarded [JENKINS-42582](https://issues.jenkins-ci.org/browse/JENKINS-42582) [#291](https://github.com/jenkinsci/kubernetes-plugin/pull/291)
* Allow specifying a different shell command other than `/bin/sh` [#287](https://github.com/jenkinsci/kubernetes-plugin/pull/287)

1.3.1
-----
* Track agents being in provisioning to avoid overprovisioning [JENKINS-47501](https://issues.jenkins-ci.org/browse/JENKINS-47501) [#289](https://github.com/jenkinsci/kubernetes-plugin/pull/289)

1.3
-----
* Implement extension points to contribute pod templates and filter them [JENKINS-49594](https://issues.jenkins-ci.org/browse/JENKINS-49594) [#288](https://github.com/jenkinsci/kubernetes-plugin/pull/288)

1.2.1
-----
* Don't persist PodTemplateMap and implement onResume in PodTemplateStep. Will prevent potential leaking of dynamic pod templates definitions across restarts [JENKINS-47759](https://issues.jenkins-ci.org/browse/JENKINS-47759) [#285](https://github.com/jenkinsci/kubernetes-plugin/pull/285)
* Provide injection of run context (build level) environment variables for container steps in declarative pipeline usage [JENKINS-49465](https://issues.jenkins-ci.org/browse/JENKINS-49465) [#285](https://github.com/jenkinsci/kubernetes-plugin/pull/285)
* Fix global environment variables [JENKINS-47376](https://issues.jenkins-ci.org/browse/JENKINS-47376) [#245](https://github.com/jenkinsci/kubernetes-plugin/pull/245)

1.2
-----
* Move PodTemplate -> Pod conversion to PodTemplateBuilder [#276](https://github.com/jenkinsci/kubernetes-plugin/pull/276)
* Split credentials classes into new plugin [kubernetes-credentials](https://github.com/jenkinsci/kubernetes-credentials-plugin)  [#268](https://github.com/jenkinsci/kubernetes-plugin/pull/268)

1.1.4
-----
* Store definition of dynamic templates to a separate configuration than Kubernetes cloud [JENKINS-49166](https://issues.jenkins-ci.org/browse/JENKINS-49166) [#279](https://github.com/jenkinsci/kubernetes-plugin/pull/279)
  * This can cause some side effects on the order the templates are picked, see
    * [JENKINS-49366](https://issues.jenkins-ci.org/browse/JENKINS-49366) Nested podTemplate stopped working in 1.1.4
    * [JENKINS-49313](https://issues.jenkins-ci.org/browse/JENKINS-49313) Incorrect podTemplate deployed starting in 1.1.4
* Prevent unneeded exec operations [#239](https://github.com/jenkinsci/kubernetes-plugin/pull/239)

1.1.3
-----
* Fix UI support of environment variables [JENKINS-47112](https://issues.jenkins-ci.org/browse/JENKINS-47112) [#273](https://github.com/jenkinsci/kubernetes-plugin/pull/273)
* Missing call to `slave.terminate()` [#256](https://github.com/jenkinsci/kubernetes-plugin/pull/256)
* Rename slave -> agent [#258](https://github.com/jenkinsci/kubernetes-plugin/pull/258)
* Add new line when logging the agent in Jenkins [#267](https://github.com/jenkinsci/kubernetes-plugin/pull/267)

1.1.2
-----
* Namespace is erroneously autodetected as 'default' [#261](https://github.com/jenkinsci/kubernetes-plugin/pull/261)
* Do not require 2.89 for installation, revert to 2.32.1 [#263](https://github.com/jenkinsci/kubernetes-plugin/pull/263)
* IllegalStateException at hudson.XmlFile.replaceIfNotAtTopLevel [JENKINS-45892](https://issues.jenkins-ci.org/browse/JENKINS-45892) [#257](https://github.com/jenkinsci/kubernetes-plugin/pull/257)

1.1.1
-----
* Fix agent reconnection after master restart [JENKINS-47476](https://issues.jenkins-ci.org/browse/JENKINS-47476) [#244](https://github.com/jenkinsci/kubernetes-plugin/pull/244)
* Wait 5s for complete disconnection of agents to avoid stack trace [#248](https://github.com/jenkinsci/kubernetes-plugin/pull/248)
* If namespace is not provided nor autoconfigured should use `default` [#234](https://github.com/jenkinsci/kubernetes-plugin/pull/234)
* Kubernetes plugin not using cmd proc variables, affecting `sshagent` step [JENKINS-47225](https://issues.jenkins-ci.org/browse/JENKINS-47225)[#236](https://github.com/jenkinsci/kubernetes-plugin/pull/236)
* Escape quotes in environment variables [JENKINS-46670](https://issues.jenkins-ci.org/browse/JENKINS-46670)[#218](https://github.com/jenkinsci/kubernetes-plugin/pull/218)

1.1
-----
* Only allow tasks after all containers in pod are ready [#230](https://github.com/jenkinsci/kubernetes-plugin/pull/230)
* Add activeDeadlineSeconds to Pod template [#221](https://github.com/jenkinsci/kubernetes-plugin/pull/221)
* Default podTemplate slaveConnectTimeout to 100 [#235](https://github.com/jenkinsci/kubernetes-plugin/pull/235)
* Allow overriding HOME env var and remove duplicated env vars [#224](https://github.com/jenkinsci/kubernetes-plugin/pull/224)
* Rename jenkinsci/jnlp-slave -> jenkins/jnlp-slave and upgrade to 3.10-1 [#231](https://github.com/jenkinsci/kubernetes-plugin/pull/231)
* Do not use a tty to prevent nohup.out from being created [JENKINS-46087](https://issues.jenkins-ci.org/browse/JENKINS-46085) [#212](https://github.com/jenkinsci/kubernetes-plugin/pull/222)
* Pod annotations cannot contain duplicate keys when combining pod templates [#220](https://github.com/jenkinsci/kubernetes-plugin/pull/220)
* Kubernetes agents not getting deleted in Jenkins after pods are deleted [JENKINS-35246](https://issues.jenkins-ci.org/browse/JENKINS-35246) [#217](https://github.com/jenkinsci/kubernetes-plugin/pull/217)
* Remove unused JENKINS_JNLP_URL env var [#219](https://github.com/jenkinsci/kubernetes-plugin/pull/219)

1.0
-----

* `containerLog` step to get the logs of a container running in the agent pod [JENKINS-46085](https://issues.jenkins-ci.org/browse/JENKINS-46085) [#195](https://github.com/jenkinsci/kubernetes-plugin/pull/195)
* Autoconfigure cloud if kubernetes url is not set [#208](https://github.com/jenkinsci/kubernetes-plugin/pull/208)
* Change containerCap and instanceCap 0 to mean do not use [JENKINS-45845](https://issues.jenkins-ci.org/browse/JENKINS-45845) [#199](https://github.com/jenkinsci/kubernetes-plugin/pull/199)
* Add environment variables to container from a secret [JENKINS-39867](https://issues.jenkins-ci.org/browse/JENKINS-39867) [#162](https://github.com/jenkinsci/kubernetes-plugin/pull/162)
 * Deprecate `containerEnvVar` for `envVar` and added `secretEnvVar`
* Enable setting slaveConnectTimeout in podTemplate defined in pipeline [#213](https://github.com/jenkinsci/kubernetes-plugin/pull/213)
* Read Jenkins URL from cloud configuration or `KUBERNETES_JENKINS_URL` env var [#216](https://github.com/jenkinsci/kubernetes-plugin/pull/216)
* Make `withEnv` work inside a container [JENKINS-46278](https://issues.jenkins-ci.org/browse/JENKINS-46278) [#204](https://github.com/jenkinsci/kubernetes-plugin/pull/204)
* Close resource leak, fix broken pipe error. Make number of concurrent requests to Kubernetes configurable [JENKINS-40825](https://issues.jenkins-ci.org/browse/JENKINS-40825) [#182](https://github.com/jenkinsci/kubernetes-plugin/pull/182)
* Delete pods in the cloud namespace when pod namespace is not defined [JENKINS-45910](https://issues.jenkins-ci.org/browse/JENKINS-45910) [#192](https://github.com/jenkinsci/kubernetes-plugin/pull/192)
* Use `Util.replaceMacro` instead of our custom replacement logic. Behavior change: when a var is not defined it is not replaced, ie. `${key1} or ${key2} or ${key3}` -> `value1 or value2 or ${key3}` [#198](https://github.com/jenkinsci/kubernetes-plugin/pull/198)
* Allow to create non-configurable instances programmatically [#191](https://github.com/jenkinsci/kubernetes-plugin/pull/191)
* Do not cache kubernetes connection to reflect config changes and credential expiration [JENKINS-39867](https://issues.jenkins-ci.org/browse/JENKINS-39867) [#189](https://github.com/jenkinsci/kubernetes-plugin/pull/189)
* Inherit podAnnotations when inheriting pod templates [#209](https://github.com/jenkinsci/kubernetes-plugin/pull/209)
* Remove unneeded plugin dependencies, make pipeline-model-extensions optional [#214](https://github.com/jenkinsci/kubernetes-plugin/pull/214)

0.12
-----

* Add an experimental Declarative Agent extension for Kubernetes [JENKINS-41758](https://issues.jenkins-ci.org/browse/JENKINS-41758) [#127](https://github.com/jenkinsci/kubernetes-plugin/pull/127)
* Implement Port mapping [#165](https://github.com/jenkinsci/kubernetes-plugin/pull/165)
* Support idleMinutes field in pipeline [#154](https://github.com/jenkinsci/kubernetes-plugin/pull/154)
* Add command liveness probe support [#158](https://github.com/jenkinsci/kubernetes-plugin/pull/158)
* Add toggle for node usage mode [#158](https://github.com/jenkinsci/kubernetes-plugin/pull/158)
* Add namespace support on PodTemplate.
* Make PodTemplate optional within pipeline [JENKINS-42315](https://issues.jenkins-ci.org/browse/JENKINS-42315)
* Make Slave Jenkins connection timeout configurable [#141](https://github.com/jenkinsci/kubernetes-plugin/pull/141)
* Fix durable pipeline PID NumberFormatException [JENKINS-42048](https://issues.jenkins-ci.org/browse/JENKINS-42048) [#157](https://github.com/jenkinsci/kubernetes-plugin/pull/157)
* Don't provision nodes if there are no PodTemplates set to usage mode Normal [#171](https://github.com/jenkinsci/kubernetes-plugin/pull/171)
* Refactoring add/set methods in PodTemplate [#173](https://github.com/jenkinsci/kubernetes-plugin/pull/173)
* Delete the build pod after we have finished with the template block [#172](https://github.com/jenkinsci/kubernetes-plugin/pull/172)
* Default to use the kubernetes.default.svc.cluster.local endpoint
* Do not print stack trace on ConnectException
* Upgrade kubernetes client to 2.3.1 [JENKINS-44189](https://issues.jenkins-ci.org/browse/JENKINS-42048)
* Step namespace should have priority over anything else [#161](https://github.com/jenkinsci/kubernetes-plugin/pull/161)
* Wait for pod to exist up to 60 seconds before erroring [#155](https://github.com/jenkinsci/kubernetes-plugin/pull/155)
* Catch IOException on ContainerExecProc#kill
* Do not print stack trace on connection exception
* Restore random naming for pipeline managed pod templates.
* Dir context is not honored by shell step [JENKINS-40925](https://issues.jenkins-ci.org/browse/JENKINS-40925) [#146](https://github.com/jenkinsci/kubernetes-plugin/pull/146)
* Limit pod name to 63 characters, and change the randomly generated string [#143](https://github.com/jenkinsci/kubernetes-plugin/pull/143)
* Fix workingDir inheritance error [#136](https://github.com/jenkinsci/kubernetes-plugin/pull/136)
* Use name instead of label for the nesting stack [#137](https://github.com/jenkinsci/kubernetes-plugin/pull/137)
* Exception in configure page when 'Kubernetes URL' isn't filled [JENKINS-45282](https://issues.jenkins-ci.org/browse/JENKINS-45282) [#174](https://github.com/jenkinsci/kubernetes-plugin/pull/174)
* kubectl temporary config file should work where Jenkins project contains spaces [#178](https://github.com/jenkinsci/kubernetes-plugin/pull/178)
* Thread/connection leak [#177](https://github.com/jenkinsci/kubernetes-plugin/pull/177)


0.11
-----

* Pod Template "Annotations" Field [#105](https://github.com/jenkinsci/kubernetes-plugin/pull/105)
* The workspace volume is now configurable [#114](https://github.com/jenkinsci/kubernetes-plugin/pull/114)
* Allow the user to configure a pod template that will be used for providing the default values. [#133](https://github.com/jenkinsci/kubernetes-plugin/pull/133)
* Cleanup environment variable mapping, allow overriding HOME env variable [#128](https://github.com/jenkinsci/kubernetes-plugin/pull/128)
* When upgrading from <0.9 set the container name to jnlp. To avoid creating an extra container, the one that exists and the new jnlp auto generated [#132](https://github.com/jenkinsci/kubernetes-plugin/pull/132)
* Make the name field in the pod template the pod name [#134](https://github.com/jenkinsci/kubernetes-plugin/pull/134)
* [JENKINS-41847] NPE in addProvisionedSlave when label is null [#129](https://github.com/jenkinsci/kubernetes-plugin/pull/129)
* [JENKINS-41725] NPE in PodTemplateUtils.combine [#130](https://github.com/jenkinsci/kubernetes-plugin/pull/130)
* [JENKINS-41287] Fix error when job contains spaces [#131](https://github.com/jenkinsci/kubernetes-plugin/pull/131)
* Remove node if pod startup fails [#122](https://github.com/jenkinsci/kubernetes-plugin/pull/122)
* Avoid NPE if cloud is deleted or renamed [#118](https://github.com/jenkinsci/kubernetes-plugin/pull/118)
* Fixing deletion of containers in pod templates, containers property is databound [#113](https://github.com/jenkinsci/kubernetes-plugin/pull/113)
* Prevent NPE in PodTemplateAction [#112](https://github.com/jenkinsci/kubernetes-plugin/pull/112)
* [JENKINS-40457] java.lang.ArrayStoreException when a image pull secret is defined [#111](https://github.com/jenkinsci/kubernetes-plugin/pull/111)

0.10
-----

* **NOTE if you have defined a JNLP container in your Pod definition**, you need to remove it or rename it to `jnlp`, otherwise a new container called `jnlp` will be created. Set "Arguments to pass to the command" to `${computer.jnlpmac} ${computer.name}`
* Fixing checkbox serialization by jelly views [#110](https://github.com/jenkinsci/kubernetes-plugin/pull/110)
* Do not throw exceptions in the test configuration page [#107](https://github.com/jenkinsci/kubernetes-plugin/pull/107)
* Upgrade to the latest kubernetes-client version. [#106](https://github.com/jenkinsci/kubernetes-plugin/pull/106)
* feat: make pipeline support instanceCap field [#102](https://github.com/jenkinsci/kubernetes-plugin/pull/102)
* Instantiating Kubernetes Client with proper config in Container Steps [#104](https://github.com/jenkinsci/kubernetes-plugin/pull/104)
* Fix NPE when slaves are read from disk [#103](https://github.com/jenkinsci/kubernetes-plugin/pull/103)
* [JENKINS-39867] Upgrade fabric8 to 1.4.26 [#101](https://github.com/jenkinsci/kubernetes-plugin/pull/101)
* The pod watcher now checks readiness of the right pod. [#97](https://github.com/jenkinsci/kubernetes-plugin/pull/97)
* Fix logic for waitUntilContainerIsReady [#95](https://github.com/jenkinsci/kubernetes-plugin/pull/95)
* instanceCap is not used in pipeline [#92](https://github.com/jenkinsci/kubernetes-plugin/pull/92)
* Allow nesting of templates for inheritance. [#94](https://github.com/jenkinsci/kubernetes-plugin/pull/94)
* Wait until all containers are in ready state before starting the slave [#93](https://github.com/jenkinsci/kubernetes-plugin/pull/93)
* Adding basic retention for idle slaves, using the idleTimeout setting properly [#91](https://github.com/jenkinsci/kubernetes-plugin/pull/91)
* Improve the inheritFrom functionality to better cover containers and volumes. [#84](https://github.com/jenkinsci/kubernetes-plugin/pull/84)
* Fix null pointer exceptions. [#89](https://github.com/jenkinsci/kubernetes-plugin/pull/89)
* fix PvcVolume jelly templates path [#90](https://github.com/jenkinsci/kubernetes-plugin/pull/90)
* Added tool installations to the pod template. [#85](https://github.com/jenkinsci/kubernetes-plugin/pull/85)
* fix configmap volume name [#87](https://github.com/jenkinsci/kubernetes-plugin/pull/87)
* set the serviceAccount when creating new pods [#86](https://github.com/jenkinsci/kubernetes-plugin/pull/86)
* Read and connect timeout are now correctly used to configure the client. [#82](https://github.com/jenkinsci/kubernetes-plugin/pull/82)
* Fix nodeSelector in podTemplate [#83](https://github.com/jenkinsci/kubernetes-plugin/pull/83)
* Use the client's namespace when deleting a pod (fixes a regression preventing pods to delete). [#81](https://github.com/jenkinsci/kubernetes-plugin/pull/81)


0.9
-----

* Make it possible to define more than one container inside a pod.
* Add new pod template step which allows defining / overriding a pod template from a pipeline script.
* Introduce pipeline step that allows choosing one of the containers of the pod and have all 'sh' steps executed there.
* allow setting dynamic pod volumes in pipelines
* Add support for persistent volume claims
* Add support for containerEnvVar's in pipelines
* [JENKINS-37087] Handle multiple labels per pod correctly
* [JENKINS-37087] Iterate over all matching templates
* Fix slave description and labels
* [JENKINS-38829] Add help text for Kubernetes server certificate
* #59: Allow blank namespace and reuse whatever is discovered by the client.
* Ensure instanceCap defaults to unlimited
* Add Jenkins computer name to the container env vars
* Split arguments having quotes into account
* Allow the user to enable pseudo-TTY on container level.
* Use provided arguments without forcing jnlpmac and name into them. Provide placeholders for jnlpmac and name for the user to use. Fallback container uses as default arguments jnlpmac and name.
* Split volume classes into their own package (#77)

0.8
-----

* Add ability to define list of image pull secrets for pod template
* Fix name printing
* [JENKINS-36253] Add Annotations to Pod Template
* Add support for NFS volumes (#63)
* Change health check url for one that works (Jenkins 2 enables CSRF protection and security by default)
* Issue #59 Allow autodiscovery of namespace
* Update kubernetes-client
* Add support for service account

0.7
-----

* Set HOME and working dir for build to execute into slave agent remoteFS (#57)
* JENKINS-34840 Fix NPE when node selector is null
* Add resouce request and limit to container
* Generate Cloud Slave node base on pod template name
* check for null prior to ranging over template.getEnvVars() (#61)
* Fix StringIndexOutOfBoundsException in slave name

0.6
-----

* Add support for secrets and empty dir volumes
* Add support for nodeSelector in pod templates
* Add support for storing OpenShift OAuth access token as credential
* Allow client certificate as kubernetes api credentials (JENKINS-30894)
* Fix ArrayIndexOutOfBoundsException building node selector map when field is empty on the configuration (JENKINS-33649)
* Add support for env variables
* Add checkbox for image pull policy

0.5
-----

* Update fabric8 client for Kubernetes to 2.2.16
* Use a replication controller to run Jenkins master
* Generate OpenShift OAuth Bearer token on demand based on user credentials
* Add support for Container Cleanup Timeout
* Fix JENKINS-31076 — Proper message if credentials are not defined or not found
* Add support for hostPath volumes

0.4.1
-----

* Avoid looooooong slave name
* Support token based authentication for CLI

0.4
-----

* Use a Bearer token to connect to API master (typically to connect to OpenShift Origin)
* Enable a dedicated credential type when jenkins do run inside kubernetes with service account enabled, so cloud can be setup with a fixed configuration, but actual credentials get injected at runtime

0.2
-----

* Replace obsolete Kubernetes client library with Fabric8
* Let user configure the kubernetes cluster CA root certificate for self-signed deployment (typically, Google Container Engine)
* Upgrade Docker-plugin dependency to 0.9.4 - warning: 0.10 is incompatible

0.1
-----

* Initial implementation
