#!/bin/bash
set -euxo pipefail
function cleanup() {
    kubectl describe pod
}
trap cleanup EXIT

kubectl get ns kubernetes-plugin-test || kubectl create ns kubernetes-plugin-test
kubectl get ns kubernetes-plugin-test-overridden-namespace || kubectl create ns kubernetes-plugin-test-overridden-namespace
kubectl config set-context --current --namespace=kubernetes-plugin-test
port_offset=$RANDOM
http_port=$((2000 + $port_offset))
tcp_port=$((2001 + $port_offset))
kubectl delete --ignore-not-found --now pod jenkins
sed "s/@HTTP_PORT@/$http_port/g; s/@TCP_PORT@/$tcp_port/g" < test-in-k8s.yaml | kubectl apply -f -
kubectl wait --for=condition=Ready --timeout=15m pod/jenkins
kubectl exec jenkins -- mkdir /checkout
kubectl cp pom.xml jenkins:/checkout/pom.xml
kubectl cp .mvn jenkins:/checkout/.mvn
kubectl cp src jenkins:/checkout/src
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
        -f /checkout \
        -DconnectorHost=0.0.0.0 \
        -Dport=$http_port \
        -DslaveAgentPort=$tcp_port \
        -Djenkins.host.address=jenkins.kubernetes-plugin-test.svc.cluster.local \
        -Dmaven.test.failure.ignore \
        $args
kubectl exec jenkins -- sh -c 'fgrep skipped /checkout/target/surefire-reports/*.xml' || :
