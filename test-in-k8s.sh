#!/usr/bin/env bash
set -euxo pipefail
function cleanup() {
    kubectl describe pod
}
trap cleanup EXIT

kubectl get ns kubernetes-plugin-test || kubectl create ns kubernetes-plugin-test
kubectl get ns kubernetes-plugin-test-overridden-namespace || kubectl create ns kubernetes-plugin-test-overridden-namespace
kubectl config set-context --current --namespace=kubernetes-plugin-test
port_offset=$RANDOM
http_port=$((2000 + port_offset))
tcp_port=$((2001 + port_offset))
kubectl delete --ignore-not-found --now pod jenkins
sed "s/@HTTP_PORT@/$http_port/g; s/@TCP_PORT@/$tcp_port/g" < test-in-k8s.yaml | kubectl apply -f -
kubectl wait --for=condition=Ready --timeout=15m pod/jenkins
# Copy temporary split files
kubectl exec jenkins -- mkdir "$WORKSPACE_TMP"
tar cf - "$WORKSPACE_TMP" | kubectl exec -i jenkins -- tar xf - -C "$WORKSPACE_TMP"
# Copy plugin files
kubectl exec jenkins -- mkdir /checkout
tar cf - pom.xml .mvn src | kubectl exec -i jenkins -- tar xf - -C /checkout
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
        verify \
        "$@"
kubectl exec jenkins -- sh -c 'fgrep skipped /checkout/target/surefire-reports/*.xml' || :
