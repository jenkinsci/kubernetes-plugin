CHANGELOG
=========

0.10
----

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
---

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
