CHANGELOG
=========

* Pod Template "Annotations" Field [#105](https://github.com/jenkinsci/kubernetes-plugin/pull/105)
* The workspace volume is now configurable [#114](https://github.com/jenkinsci/kubernetes-plugin/pull/114)

* When upgrading from <0.9 set the container name to jnlp. To avoid creating an extra container, the one that exists and the new jnlp auto generated [#132](https://github.com/jenkinsci/kubernetes-plugin/pull/132)
* Remove node if pod startup fails [#122](https://github.com/jenkinsci/kubernetes-plugin/pull/122)
* Avoid NPE if cloud is deleted or renamed [#118](https://github.com/jenkinsci/kubernetes-plugin/pull/118)
* Fixing deletion of containers in pod templates, containers property is databound [#113](https://github.com/jenkinsci/kubernetes-plugin/pull/113)
* Prevent NPE in PodTemplateAction [#112](https://github.com/jenkinsci/kubernetes-plugin/pull/112)
* [JENKINS-40457] java.lang.ArrayStoreException when a image pull secret is defined [#111](https://github.com/jenkinsci/kubernetes-plugin/pull/111)

0.10
-----

* **NOTE if you have defined a JNLP container in your Pod definition**, you need to remove it or rename it to `jnlp`, otherwise a new container called `jnlp` will be created.
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
