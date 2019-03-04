CHANGELOG
=========

Known issues
------------
* Nested pod templates and inheritance issues [JENKINS-49700](https://issues.jenkins-ci.org/browse/JENKINS-49700)

See the full list of issues at [JIRA](https://issues.jenkins-ci.org/issues/?filter=15575)

1.14.8
------
* Do not close Kubernetes client after `containerLog` step [#435](https://github.com/jenkinsci/kubernetes-plugin/pull/435) [JENKINS-55392](https://issues.jenkins-ci.org/browse/JENKINS-55392)
* Upgrade kubernetes-client to 4.1.3. Pass `exec` buffer size using new method [#431](https://github.com/jenkinsci/kubernetes-plugin/pull/431) [JENKINS-50429](https://issues.jenkins-ci.org/browse/JENKINS-50429)

1.14.7
------
* Use a bigger buffer for stdin on kubernetes exec [#427](https://github.com/jenkinsci/kubernetes-plugin/pull/427) [JENKINS-50429](https://issues.jenkins-ci.org/browse/JENKINS-50429)
* Improve labels and help text for cloud and pod templates cap [#422](https://github.com/jenkinsci/kubernetes-plugin/pull/422)

1.14.6
------
* Add a system property to override default Slave Connect Timeout in seconds [#432](https://github.com/jenkinsci/kubernetes-plugin/pull/432)
* Add build url as default annotation [#433](https://github.com/jenkinsci/kubernetes-plugin/pull/433) [JENKINS-56133](https://issues.jenkins-ci.org/browse/JENKINS-56133)
* Update kubernetes client to 4.1.2 [#434](https://github.com/jenkinsci/kubernetes-plugin/pull/434) [JENKINS-52593](https://issues.jenkins-ci.org/browse/JENKINS-52593)
* Use a watcher to monitor pod status while launching the agent [#423](https://github.com/jenkinsci/kubernetes-plugin/pull/423)

1.14.5
------
* Expire Kubernetes clients after one day and make it configurable [#429](https://github.com/jenkinsci/kubernetes-plugin/pull/429) [JENKINS-56140](https://issues.jenkins-ci.org/browse/JENKINS-56140)
* Allow non admin to call `getContainers` and `getPodEvents` returning an empty list [#430](https://github.com/jenkinsci/kubernetes-plugin/pull/430) [JENKINS-56155](https://issues.jenkins-ci.org/browse/JENKINS-56155)

1.14.4
------
* Set `nodeUsageMode` to `EXCLUSIVE` as default [#386](https://github.com/jenkinsci/kubernetes-plugin/pull/386)
* Add `slaveConnectTimeout` and `namespace` to declarative pipeline [#421](https://github.com/jenkinsci/kubernetes-plugin/pull/421) [JENKINS-55960](https://issues.jenkins-ci.org/browse/JENKINS-55960)

1.14.3
------
* Use label as pod name when not set [#375](https://github.com/jenkinsci/kubernetes-plugin/pull/375)
* Upgrade dependencies to latest versions [#417](https://github.com/jenkinsci/kubernetes-plugin/pull/417) [#307](https://github.com/jenkinsci/kubernetes-plugin/pull/307)

1.14.2
------
* Require Jenkins 2.138.4 instead of 2.150.1 [#413](https://github.com/jenkinsci/kubernetes-plugin/pull/413)
* Combine env vars into a single set before writing once in container shell execution [#393](https://github.com/jenkinsci/kubernetes-plugin/pull/393) [JENKINS-50429](https://issues.jenkins-ci.org/browse/JENKINS-50429)
* Fail faster if a pod enters in error state during provisioning [#414](https://github.com/jenkinsci/kubernetes-plugin/pull/414) 

1.14.1
------
* Allow setting namespace from Pod yaml [#405](https://github.com/jenkinsci/kubernetes-plugin/pull/405) [JENKINS-51610](https://issues.jenkins-ci.org/browse/JENKINS-51610)

1.14.0
------
* Add page to Kubernetes nodes to show pod events [#408](https://github.com/jenkinsci/kubernetes-plugin/pull/408)

1.13.9
------
* Require Jenkins 2.150.1 [#411](https://github.com/jenkinsci/kubernetes-plugin/pull/411)
* Do not wait for pod if it has been deleted or if it has failed [#410](https://github.com/jenkinsci/kubernetes-plugin/pull/410) [#412](https://github.com/jenkinsci/kubernetes-plugin/pull/412)

1.13.8
------
* Don't close kubernetes client upon cache removal [#407](https://github.com/jenkinsci/kubernetes-plugin/pull/407) [JENKINS-55138](https://issues.jenkins-ci.org/browse/JENKINS-55138)

1.13.7
------
* Add missing field maxRequestsPerHost to copy constructor [#403](https://github.com/jenkinsci/kubernetes-plugin/pull/403)
* Fix maxRequestsPerHost form validation [#400](https://github.com/jenkinsci/kubernetes-plugin/pull/400)

1.13.6
------
* Prevent multiple instances of KubernetesClient that can cause memory leaks with multiple API http connections [#397](https://github.com/jenkinsci/kubernetes-plugin/pull/397) [JENKINS-54770](https://issues.jenkins-ci.org/browse/JENKINS-54770)
  * Note that this will enforce the limit of connections to the Kubernetes API and you may need to increase the value of *Max connections to Kubernetes API* if you see errors like [`JENKINS-40825`](https://issues.jenkins-ci.org/browse/JENKINS-40825)`: interrupted while waiting for websocket connection`


1.13.5
------
* Populate jnlp tunnel in the jnlp endpoint to launch agent whether Jenkins is behind load balancer or not [#389](https://github.com/jenkinsci/kubernetes-plugin/pull/389)
* Combine parent pod template ports with children [#340](https://github.com/jenkinsci/kubernetes-plugin/pull/340) [JENKINS-50932](https://issues.jenkins-ci.org/browse/JENKINS-50932)

1.13.4
------
* Allow custom workspace in declarative pipeline [#380](https://github.com/jenkinsci/kubernetes-plugin/pull/380) [JENKINS-53817](https://issues.jenkins-ci.org/browse/JENKINS-53817)

1.13.3
------
* Upgrade kubernetes-client to 4.1.0 [#391](https://github.com/jenkinsci/kubernetes-plugin/pull/391) [JENKINS-52593](https://issues.jenkins-ci.org/browse/JENKINS-52593)

1.13.2
------
* Pod name is detected as `localhost` in Bluemix IKS [#388](https://github.com/jenkinsci/kubernetes-plugin/pull/388) [JENKINS-53297](https://issues.jenkins-ci.org/browse/JENKINS-53297)

1.13.1
------
* Allow adding jenkins job metadata to the pods using the KubernetesComputer extenion point [#383](https://github.com/jenkinsci/kubernetes-plugin/pull/383)

1.13.0
------
* Display Pod log for Kubernetes agents in the node view [#367](https://github.com/jenkinsci/kubernetes-plugin/pull/367)

1.12.9
------
* Declarative pipeline: stdin/stdout/stderr of a remote process are not redirected. Do not wrap the default `jnlp` container calls in `container` steps [#377](https://github.com/jenkinsci/kubernetes-plugin/pull/377) [JENKINS-53422](https://issues.jenkins-ci.org/browse/JENKINS-53422)

1.12.8
------
* Handle null retention policy resulting from direct xml pod template injection (seen during agent termination) [#381](https://github.com/jenkinsci/kubernetes-plugin/pull/381)

1.12.7
------
* Fix nested Pod Templates support [#382](https://github.com/jenkinsci/kubernetes-plugin/pull/382) [JENKINS-50196](https://issues.jenkins-ci.org/browse/JENKINS-50196)
* Fix pod spec display in build logs [#384](https://github.com/jenkinsci/kubernetes-plugin/pull/384)

1.12.6
------

* Container and instance cap are not honored when requesting lots of slaves [#374](https://github.com/jenkinsci/kubernetes-plugin/pull/374) [JENKINS-53313](https://issues.jenkins-ci.org/browse/JENKINS-53313)

1.12.5
------

* Check for nulls in older kubernetes versions, fixes some NPEs in Kubernetes 1.5 [#378](https://github.com/jenkinsci/kubernetes-plugin/pull/378) [JENKINS-53370](https://issues.jenkins-ci.org/browse/JENKINS-53370)

1.12.4
------

* Add volumes from pod template to JNLP container [#371](https://github.com/jenkinsci/kubernetes-plugin/pull/371) [JENKINS-50879](https://issues.jenkins-ci.org/browse/JENKINS-50879)
* Chinese localization [#368](https://github.com/jenkinsci/kubernetes-plugin/pull/368)[#370](https://github.com/jenkinsci/kubernetes-plugin/pull/370)

1.12.3
------

* Upgrade Jenkins to 2.121.2 [#365](https://github.com/jenkinsci/kubernetes-plugin/pull/365)

1.12.2
------

* Using declarative, environment variables like COMMIT_ID, GIT_BRANCH are not populated. Use CheckoutScript to populate environment [#364](https://github.com/jenkinsci/kubernetes-plugin/pull/364) [JENKINS-52623](https://issues.jenkins-ci.org/browse/JENKINS-52623)

1.12.1
------

* Upgrade kubernetes-client to 4.0.0. Drops support for OpenShift <1.6 [#358](https://github.com/jenkinsci/kubernetes-plugin/pull/358) [JENKINS-53363](https://issues.jenkins-ci.org/browse/JENKINS-53363)
* Fix defaults for Pod Retention on Pod templates [#363](https://github.com/jenkinsci/kubernetes-plugin/pull/363) [JENKINS-48149](https://issues.jenkins-ci.org/browse/JENKINS-48149)

1.12.0
------

* Add optional usage restriction for a Kubernetes cloud using folder properties [#282](https://github.com/jenkinsci/kubernetes-plugin/pull/282)

1.11.0
------

* Add Pod Retention policies to keep pods around on failure [#354](https://github.com/jenkinsci/kubernetes-plugin/pull/354) [JENKINS-48149](https://issues.jenkins-ci.org/browse/JENKINS-48149)

1.10.2
------

* Global configuration `testConnection` using GET allows stealing credentials + CSRF [SECURITY-1016](https://issues.jenkins-ci.org/browse/SECURITY-1016)

1.10.1
-------

* Tool Location overwrites are not preserved [#318](https://github.com/jenkinsci/kubernetes-plugin/pull/318) [JENKINS-44285](https://issues.jenkins-ci.org/browse/JENKINS-44285)

1.10.0
-------

* Add `yamlFile` option for Declarative agent to read yaml definition from a different file [#355](https://github.com/jenkinsci/kubernetes-plugin/pull/355) [JENKINS-52259](https://issues.jenkins-ci.org/browse/JENKINS-52259)

1.9.3
-----

* Avoid streaming to 2 similar OutputStreams [#356](https://github.com/jenkinsci/kubernetes-plugin/pull/356)

1.9.2
-----

* Combine all resources declared in requests and limits not just CPU and memory [#350](https://github.com/jenkinsci/kubernetes-plugin/pull/350)

1.9.1
-----

* Jenkins master in windows changes the file separator of `mountPath` incorrectly [#308](https://github.com/jenkinsci/kubernetes-plugin/pull/308) [JENKINS-47178](https://issues.jenkins-ci.org/browse/JENKINS-47178)

1.9.0
-----

* Update parent and Jenkins versions [#349](https://github.com/jenkinsci/kubernetes-plugin/pull/349)

1.8.4
-----

* Fix mountPath error provisioning `mountPath: Required value` [#346](https://github.com/jenkinsci/kubernetes-plugin/pull/346) [JENKINS-50525](https://issues.jenkins-ci.org/browse/JENKINS-50525)

1.8.3
-----

* Preserve unsupported directives in `PodTemplate` yaml, add explicit support for envFrom
 [#348](https://github.com/jenkinsci/kubernetes-plugin/pull/348)

1.8.2
-----

* Do not emit empty strings for resource requests/limits
 [#342](https://github.com/jenkinsci/kubernetes-plugin/pull/342)

1.8.1
-----
* Get the exit code the correct way. Solves problems with many pipeline steps that rely on tool outputs [#300](https://github.com/jenkinsci/kubernetes-plugin/pull/300) [JENKINS-50392](https://issues.jenkins-ci.org/browse/JENKINS-50392)

1.8.0
-----
* Validate label and container names with regex [#332](https://github.com/jenkinsci/kubernetes-plugin/pull/332) [#343](https://github.com/jenkinsci/kubernetes-plugin/pull/343) [JENKINS-51248](https://issues.jenkins-ci.org/browse/JENKINS-51248)

1.7.1
-----
* Do not print credentials in build output or logs. Only affects certain pipeline steps like `withDockerRegistry`. `sh` step is not affected [SECURITY-883](https://issues.jenkins-ci.org/browse/SECURITY-883)

1.7.0
-----
* Add option to apply caps only on alive pods [#252](https://github.com/jenkinsci/kubernetes-plugin/pull/252)
* Add idleMinutes to pod template in declarative pipeline [#336](https://github.com/jenkinsci/kubernetes-plugin/pull/336) [JENKINS-51569](https://issues.jenkins-ci.org/browse/JENKINS-51569)

1.6.4
-----
* Use Jackson and Apache HttpComponents Client libraries from API plugins [#333](https://github.com/jenkinsci/kubernetes-plugin/pull/333) [JENKINS-51582](https://issues.jenkins-ci.org/browse/JENKINS-51582)

1.6.3
-----
* Merge labels from yaml [#326](https://github.com/jenkinsci/kubernetes-plugin/pull/326) [JENKINS-51137](https://issues.jenkins-ci.org/browse/JENKINS-51137)
* Instance cap reached with preexisting pods due to lack of labels [#325](https://github.com/jenkinsci/kubernetes-plugin/pull/325) [JENKINS-50268](https://issues.jenkins-ci.org/browse/JENKINS-50268)

1.6.2
-----
* Transfer any master proxy related envs that the remoting jar uses to the pod templates with `addMasterProxyEnvVars` option [#321](https://github.com/jenkinsci/kubernetes-plugin/pull/321)

1.6.1
-----
* Some fields are not inherited from parent template (InheritFrom, InstanceCap, SlaveConnectTimeout, IdleMinutes, ActiveDeadlineSeconds, ServiceAccount, CustomWorkspaceVolumeEnabled) [#319](https://github.com/jenkinsci/kubernetes-plugin/pull/319)

1.6.0
-----
* Support multiple containers in declarative pipeline [#306](https://github.com/jenkinsci/kubernetes-plugin/pull/306) [JENKINS-48135](https://issues.jenkins-ci.org/browse/JENKINS-48135)
* Expose pod configuration via yaml to UI and merge tolerations when inheriting [#311](https://github.com/jenkinsci/kubernetes-plugin/pull/311)
* Resolve NPE merging yaml when resource requests/limits are not set [#310](https://github.com/jenkinsci/kubernetes-plugin/pull/310)
* Do not pass arguments to jnlp container [#315](https://github.com/jenkinsci/kubernetes-plugin/pull/315) [JENKINS-50913](https://issues.jenkins-ci.org/browse/JENKINS-50913)

1.5.2
-----
* Merge default `jnlp` container options [JENKINS-50533](https://issues.jenkins-ci.org/browse/JENKINS-50533) [#305](https://github.com/jenkinsci/kubernetes-plugin/pull/305)

1.5.1
-----
* Fix duplicated volume mounts [JENKINS-50525](https://issues.jenkins-ci.org/browse/JENKINS-50525) [#303](https://github.com/jenkinsci/kubernetes-plugin/pull/303)
* Use the correct agent namespace in logs [#304](https://github.com/jenkinsci/kubernetes-plugin/pull/304)

1.5
-----
* Allow creating Pod templates from yaml. This allows setting all possible fields in Kubernetes API using yaml [JENKINS-50282](https://issues.jenkins-ci.org/browse/JENKINS-50282) [#275](https://github.com/jenkinsci/kubernetes-plugin/pull/275)
* Print agent specification upon node allocation [#302](https://github.com/jenkinsci/kubernetes-plugin/pull/302)

1.4.1
-----
* Env vars using `PATH+SOMETHING` syntax clear the previous env var [JENKINS-50437](https://issues.jenkins-ci.org/browse/JENKINS-50437) [#301](https://github.com/jenkinsci/kubernetes-plugin/pull/301)

1.4
-----
* Support passing `kubeconfig` file as credentials using secretFile credentials [JENKINS-49817](https://issues.jenkins-ci.org/browse/JENKINS-49817) [#294](https://github.com/jenkinsci/kubernetes-plugin/pull/294)
* Allow customization of NodeProvisioner.PlannedNode using extension point [#298](https://github.com/jenkinsci/kubernetes-plugin/pull/298)

1.3.3
-----
* Upgrade kubernetes-client to 3.1.10 [#271](https://github.com/jenkinsci/kubernetes-plugin/pull/271)
* Copy `jenkinsTunnel` in copy constructor [#295](https://github.com/jenkinsci/kubernetes-plugin/pull/295)

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
