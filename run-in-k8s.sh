#!/bin/bash
set -euxo pipefail
function cleanup() {
    kubectl delete -f src/main/kubernetes/service-account.yml || :
    kubectl delete --grace-period=1 pod jenkins || :
    kubectl delete svc jenkins || :
}
trap cleanup EXIT
kubectl create -f src/main/kubernetes/service-account.yml
# TODO could define this as a StatefulSet (+ Service) so that we could define a cache volume for /root/.m2/repository
kubectl run --expose --port 8000 --restart=Never --serviceaccount=jenkins --image=maven:3.6.2-jdk-8 jenkins sleep infinity
kubectl wait --for=condition=Ready pod/jenkins
kubectl exec jenkins mkdir /checkout
kubectl cp pom.xml jenkins:/checkout/pom.xml
kubectl cp src jenkins:/checkout/src
kubectl cp settings-azure.xml jenkins:/settings-azure.xml
kubectl exec jenkins -- mvn -B -ntp -s /settings-azure.xml -f /checkout -DconnectorHost=0.0.0.0 -Dport=8000 -Djenkins.host.address=jenkins test -Dtest=KubernetesPipelineTest\#runInPod
