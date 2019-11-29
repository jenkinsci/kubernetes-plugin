#!/bin/bash
set -euxo pipefail
kubectl get ns kubernetes-plugin-test || kubectl create ns kubernetes-plugin-test
kubectl get ns kubernetes-plugin-test-overridden-namespace || kubectl create ns kubernetes-plugin-test-overridden-namespace
kubectl config set-context --current --namespace=kubernetes-plugin-test
kubectl apply -f test-in-k8s.yaml
kubectl wait --for=condition=Ready --timeout=15m pod/jenkins
kubectl exec jenkins -- sh -c 'rm -rf /checkout && mkdir /checkout'
kubectl cp pom.xml jenkins:/checkout/pom.xml
kubectl cp .mvn jenkins:/checkout/.mvn
kubectl cp src jenkins:/checkout/src
kubectl cp settings-azure.xml jenkins:/settings-azure.xml
if [ -v TEST ]
then
    args="-Dtest=$TEST test"
else
    args=verify
fi
kubectl exec jenkins -- \
        mvn \
        -B \
        -ntp \
        -s /settings-azure.xml \
        -f /checkout \
        -DconnectorHost=0.0.0.0 \
        -Dport=8000 \
        -DslaveAgentPort=50000 \
        -Djenkins.host.address=jenkins.kubernetes-plugin-test.svc.cluster.local \
        -Dmaven.test.failure.ignore \
        $args
kubectl exec jenkins -- sh -c 'fgrep skipped /checkout/target/surefire-reports/*.xml' || :
