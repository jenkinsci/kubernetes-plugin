#!/bin/bash
set -euxo pipefail

export PATH=$WSTMP:$PATH
if [ \! -x $WSTMP/kind ]
then
    curl -Lo $WSTMP/kind https://github.com/kubernetes-sigs/kind/releases/download/v0.11.1/kind-$(uname | tr '[:upper:]' '[:lower:]')-amd64
    chmod +x $WSTMP/kind
fi
if [ \! -x $WSTMP/kubectl ]
then
    curl -Lo $WSTMP/kubectl https://storage.googleapis.com/kubernetes-release/release/v1.21.3/bin/$(uname | tr '[:upper:]' '[:lower:]')/amd64/kubectl
    chmod +x $WSTMP/kubectl
fi

export cluster=ci$RANDOM
export KUBECONFIG=$WSTMP/kubeconfig-$cluster
kind create cluster --name $cluster --wait 5m --config kind.yaml
function cleanup() {
    kind export logs --name $cluster $WSTMP/kindlogs || :
    kind delete cluster --name $cluster || :
    rm $KUBECONFIG
}
trap cleanup EXIT
kubectl cluster-info

DOCKER_IMAGE=$(grep -e image: test-in-k8s.yaml | cut -d ':' -f 2- | xargs)
docker pull $DOCKER_IMAGE
kind load docker-image $DOCKER_IMAGE --name $cluster

bash test-in-k8s.sh
rm -rf $WSTMP/surefire-reports
kubectl cp jenkins:/checkout/target/surefire-reports $WSTMP/surefire-reports
