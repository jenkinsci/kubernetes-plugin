#!/bin/bash
set -euxo pipefail

export PATH=$WSTMP:$PATH
if [ \! -f $WSTMP/kind ]
then
    curl -Lo $WSTMP/kind https://github.com/kubernetes-sigs/kind/releases/download/v0.5.1/kind-$(uname)-amd64
    chmod +x $WSTMP/kind
fi
if [ \! -f $WSTMP/kubectl ]
then
    curl -Lo $WSTMP/kubectl https://storage.googleapis.com/kubernetes-release/release/v1.15.3/bin/linux/amd64/kubectl
    chmod +x $WSTMP/kubectl
fi

# TODO use kind load to take better advantage of images cached in the host VM
export cluster=ci$RANDOM
kind create cluster --name $cluster --wait 5m
function cleanup() {
    kind export logs --name $cluster $WSTMP/kindlogs || :
    kind delete cluster --name $cluster || :
}
trap cleanup EXIT
export KUBECONFIG="$(kind get kubeconfig-path --name $cluster)"
kubectl cluster-info

# https://github.com/kubernetes-sigs/kind/issues/118#issuecomment-475134086
kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/205a80824bc6ea6095602458c15651f58146eae4/deploy/local-path-storage.yaml
kubectl annotate storageclass local-path storageclass.beta.kubernetes.io/is-default-class=true
kubectl delete storageclass standard

bash test-in-k8s.sh
rm -rf $WSTMP/surefire-reports
kubectl cp jenkins:/checkout/target/surefire-reports $WSTMP/surefire-reports
