FROM jenkins:1.580.2

COPY src/main/docker/plugins.txt /usr/share/jenkins/plugins.txt
RUN /usr/local/bin/plugins.sh /usr/share/jenkins/plugins.txt

COPY target/kubernetes.hpi /usr/share/jenkins/ref/plugins/

# remove executors in master
COPY src/main/docker/master-executors.groovy /usr/share/jenkins/ref/init.groovy.d/

USER root
