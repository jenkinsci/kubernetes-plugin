jenkins-kubernetes-plugin
=========================

Jenkins plugin to run dynamic slaves in a Kubernetes/Docker environment.

Based on the [Scaling Docker with Kubernetes](http://www.infoq.com/articles/scaling-docker-with-kubernetes) article,
automates the scaling of Jenkins slaves running in Kubernetes.

The plugin creates a Kubernetes Pod for each slave started,
defined by the Docker image to run, and stops it after each build.

Slaves are launched using JNLP, so it is expected that the image connects automatically to the Jenkins master.
For that some environment variables are automatically injected:

* `JENKINS_URL`: Jenkins web interface url
* `JENKINS_JNLP_URL`: url for the jnlp definition of the specific slave
* `JENKINS_SECRET`: the secret key for authentication

Tested with [`csanchez/jenkins-slave`](https://registry.hub.docker.com/u/csanchez/jenkins-slave/),
see the [Docker image source code](https://github.com/carlossg/jenkins-slave-docker).


# Configuration on Google Container Engine

Create a cluster 

    gcloud preview container clusters create jenkins --num-nodes 1 --machine-type g1-small

and note the admin password.


When using Kubernetes with self-signed certificates you need to add them to the java runtime.

Import certificate into `$JAVA_HOME/jre/lib/security/jssecacerts`
(you may need to use `sudo`):

    sudo cp -n $JAVA_HOME/jre/lib/security/cacerts $JAVA_HOME/jre/lib/security/jssecacerts
    sudo keytool -import -v -trustcacerts \
      -alias kubernetes -file ~/.config/gcloud/kubernetes/*/ca.crt \
      -keystore $JAVA_HOME/jre/lib/security/jssecacerts -keypass changeit \
      -storepass changeit

In Google Container Engine the certificate is in the master node under `/srv/kubernetes/server.cert`
or already copied in your local system under `~/.config/gcloud/kubernetes` if you used `gcloud`

[More resources on Certificates](http://erikzaadi.com/2011/09/09/connecting-jenkins-to-self-signed-certificated-servers/).

# Debugging

To inspect the json messages sent back and forth to the Kubernetes API server you can configure
a new [Jenkins log recorder](https://wiki.jenkins-ci.org/display/JENKINS/Logging) for `org.apache.http`
at `DEBUG` level.


# Building

You need to build first the [Kubernetes Java API](https://github.com/carlossg/KubernetesAPIJavaClient) dependency with `mvn install`

Then just run `mvn clean package` and copy `target/kubernetes.hpi` to Jenkins plugins folder.
