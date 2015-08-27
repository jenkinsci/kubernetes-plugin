FROM jenkins:1.609.2

COPY src/main/docker/plugins.txt /usr/share/jenkins/plugins.txt
RUN /usr/local/bin/plugins.sh /usr/share/jenkins/plugins.txt

ENV VERSION 0.3
# COPY target/kubernetes.hpi /usr/share/jenkins/ref/plugins/kubernetes.hpi
RUN curl -o /usr/share/jenkins/ref/plugins/kubernetes.hpi \
  http://repo.jenkins-ci.org/snapshots/org/csanchez/jenkins/plugins/kubernetes/0.3/kubernetes-$VERSION.hpi

# remove executors in master
COPY src/main/docker/master-executors.groovy /usr/share/jenkins/ref/init.groovy.d/

# ENV JAVA_OPTS="-Djava.util.logging.config.file=/var/jenkins_home/log.properties"
