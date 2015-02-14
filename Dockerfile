FROM jenkins:1.580.2

COPY src/main/docker/plugins.txt /usr/share/jenkins/plugins.txt
RUN /usr/local/bin/plugins.sh /usr/share/jenkins/plugins.txt

ENV VERSION 0.1-20150214.103149-1
RUN curl -o /usr/share/jenkins/ref/plugins/kubernetes.hpi \
  https://oss.sonatype.org/content/repositories/snapshots/org/csanchez/jenkins/plugins/kubernetes/0.1-SNAPSHOT/kubernetes-$VERSION.hpi

# remove executors in master
COPY src/main/docker/master-executors.groovy /usr/share/jenkins/ref/init.groovy.d/

USER root
