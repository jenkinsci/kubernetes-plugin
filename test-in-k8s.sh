#!/bin/bash
set -euxo pipefail
kubectl config set-context --current --namespace=kubernetes-plugin-test
kubectl apply -f test-in-k8s.yaml
kubectl wait --for=condition=Ready --timeout=15m pod/jenkins
kubectl exec jenkins -- sh -c 'rm -rf /checkout && mkdir /checkout'
kubectl cp pom.xml jenkins:/checkout/pom.xml
kubectl cp src jenkins:/checkout/src
kubectl cp settings-azure.xml jenkins:/settings-azure.xml
kubectl exec jenkins -- \
        mvn \
        -B \
        -ntp \
        -s /settings-azure.xml \
        -f /checkout \
        -DconnectorHost=0.0.0.0 \
        -Dport=8000 \
        -DslaveAgentPort=50000 \
        -Djenkins.host.address=jenkins \
        -Dtest=KubernetesPipelineTest\#runInPod \
        -Djenkins.test.timeout=0 \
        test
