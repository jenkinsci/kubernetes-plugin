#!/usr/bin/env bash
set -euxo pipefail

export PATH=$WORKSPACE_TMP:$PATH
if [ \! -x "$WORKSPACE_TMP/kind" ]
then
    curl -sLo "$WORKSPACE_TMP/kind" https://github.com/kubernetes-sigs/kind/releases/download/v0.17.0/kind-$(uname | tr '[:upper:]' '[:lower:]')-amd64
    chmod +x "$WORKSPACE_TMP/kind"
fi
if [ \! -x "$WORKSPACE_TMP/kubectl" ]
then
    curl -sLo "$WORKSPACE_TMP/kubectl" https://storage.googleapis.com/kubernetes-release/release/v1.25.4/bin/$(uname | tr '[:upper:]' '[:lower:]')/amd64/kubectl
    chmod +x "$WORKSPACE_TMP/kubectl"
fi

export cluster=ci$RANDOM
export KUBECONFIG="$WORKSPACE_TMP/kubeconfig-$cluster"
kind create cluster --name $cluster --wait 5m
function cleanup() {
    kind export logs --name $cluster "$WORKSPACE_TMP/kindlogs" || :
    kind delete cluster --name $cluster || :
    rm "$KUBECONFIG"
}
trap cleanup EXIT
kubectl cluster-info

PRE_LOAD_IMAGES=()
PRE_LOAD_IMAGES+=($(grep -e image: test-in-k8s.yaml | cut -d ':' -f 2- | xargs))
PRE_LOAD_IMAGES+=($(grep -h --include="*.groovy" -e "^\s*image: .*$" -R src/test/resources | sed -e "s/^[[:space:]]*image: //" | sort | uniq | grep -v "windows" | grep -v "nonexistent" | grep -v "invalid" | xargs))
for image in "${PRE_LOAD_IMAGES[@]}"
do
  docker pull "$image"
  kind load docker-image "$image" --name $cluster
done

./test-in-k8s.sh "$@"
rm -rf "$WORKSPACE_TMP/surefire-reports"
kubectl cp jenkins:/checkout/target/surefire-reports "$WORKSPACE_TMP/surefire-reports"
