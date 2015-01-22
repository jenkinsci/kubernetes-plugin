jenkins-kubernetes-plugin
=========================

Jenkins plugin to run dynamic slaves in a Kubernetes/Docker environment.

**Work in progress!!!**

Based on the [Scaling Docker with Kubernetes](http://www.infoq.com/articles/scaling-docker-with-kubernetes) article,
automates the scaling of Jenkins slaves running in Kubernetes.

The plugin creates a replication controller for each type of slave,
defined by the Docker image to run, and then scale the controller
up and down before and after each build as needed.

The images to run are expected to use [Jenkins Swarm](https://wiki.jenkins-ci.org/display/JENKINS/Swarm+Plugin), as in
[csanchez/jenkins-swarm-slave](https://registry.hub.docker.com/u/csanchez/jenkins-swarm-slave/).
Swarm slaves self register when started,
SSH agents don't make a lot of sense in a Kubernetes environment and complicate
the networking setup.


# Configuration

When using Kubernetes with self-signed certificates you need to add them to the java runtime.

Example for a Kubernetes Vagrant setup, import certificate into `$JAVA_HOME/jre/lib/security/jssecacerts`
(you may need to use `sudo`):

    cp -n $JAVA_HOME/jre/lib/security/cacerts $JAVA_HOME/jre/lib/security/jssecacerts
    keytool -import -v -trustcacerts \
      -alias kubernetes-vagrant -file ~/.kubernetes.vagrant.ca.crt \
      -keystore $JAVA_HOME/jre/lib/security/jssecacerts -keypass changeit \
      -storepass changeit

In Google Container Engine the certificate is in the master node under `/srv/kubernetes/server.cert`

[More resources on Certificates](http://erikzaadi.com/2011/09/09/connecting-jenkins-to-self-signed-certificated-servers/).

# Debugging

To inspect the json messages sent back and forth to the Kubernetes API server you can configure
a new [Jenkins log recorder](https://wiki.jenkins-ci.org/display/JENKINS/Logging) for `org.apache.http`
at `DEBUG` level.


# Building

You need to build first the [Kubernetes Java API](https://github.com/carlossg/KubernetesAPIJavaClient) dependency with `mvn install`

Then just run `mvn clean package` and copy `target/kubernetes.hpi` to Jenkins plugins folder.
