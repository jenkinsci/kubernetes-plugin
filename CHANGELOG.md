CHANGELOG
=========

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
