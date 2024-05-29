#!/usr/bin/env bash
set -euxo pipefail
cd $(dirname $0)

export PATH=$WORKSPACE_TMP:$PATH
if [ \! -x "$WORKSPACE_TMP/kind" ]
then
    curl -sLo "$WORKSPACE_TMP/kind" https://github.com/kubernetes-sigs/kind/releases/download/v0.23.0/kind-$(uname | tr '[:upper:]' '[:lower:]')-amd64
    chmod +x "$WORKSPACE_TMP/kind"
fi
if [ \! -x "$WORKSPACE_TMP/kubectl" ]
then
    curl -sLo "$WORKSPACE_TMP/kubectl" https://storage.googleapis.com/kubernetes-release/release/v1.30.1/bin/$(uname | tr '[:upper:]' '[:lower:]')/amd64/kubectl
    chmod +x "$WORKSPACE_TMP/kubectl"
fi

export cluster=ci$RANDOM
export KUBECONFIG="$WORKSPACE_TMP/kubeconfig-$cluster"
if ${MOUNT_M2:-false}
then
  ./kind-mount-m2.sh
else
  kind create cluster --name $cluster --wait 5m
fi
function cleanup() {
    kind export logs --name $cluster "$WORKSPACE_TMP/kindlogs" || :
    kind delete cluster --name $cluster || :
    rm "$KUBECONFIG"
}
trap cleanup EXIT
kubectl cluster-info

./kind-preload.sh

./test-in-k8s.sh "$@"
rm -rf "$WORKSPACE_TMP/surefire-reports"
kubectl cp jenkins:/checkout/target/surefire-reports "$WORKSPACE_TMP/surefire-reports"
