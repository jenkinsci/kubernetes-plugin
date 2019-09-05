FROM jenkins/jenkins:lts-alpine

ARG VERSION=1.15.5
#RUN /usr/local/bin/install-plugins.sh kubernetes:${VERSION}

RUN /usr/local/bin/install-plugins.sh kubernetes-client-api kubernetes-credentials docker-commons cloudbees-folder workflow-api variant durable-task
COPY target/kubernetes.hpi /usr/share/jenkins/ref/plugins/kubernetes.hpi

# RUN curl -o /usr/share/jenkins/ref/plugins/kubernetes.hpi \
#  http://repo.jenkins-ci.org/snapshots/org/csanchez/jenkins/plugins/kubernetes/0.12/kubernetes-$VERSION.hpi

# remove executors in master
COPY src/main/docker/master-executors.groovy /usr/share/jenkins/ref/init.groovy.d/

# ENV JAVA_OPTS="-Djava.util.logging.config.file=/var/jenkins_home/log.properties"
ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap -XX:MaxRAMFraction=1 -XshowSettings:vm"
