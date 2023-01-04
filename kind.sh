#!/bin/bash
set -euxo pipefail

export PATH=$WSTMP:$PATH
if [ \! -x $WSTMP/kind ]
then
    curl -sLo $WSTMP/kind https://github.com/kubernetes-sigs/kind/releases/download/v0.17.0/kind-$(uname | tr '[:upper:]' '[:lower:]')-amd64
    chmod +x $WSTMP/kind
fi
if [ \! -x $WSTMP/kubectl ]
then
    curl -sLo $WSTMP/kubectl https://storage.googleapis.com/kubernetes-release/release/v1.25.4/bin/$(uname | tr '[:upper:]' '[:lower:]')/amd64/kubectl
    chmod +x $WSTMP/kubectl
fi

export cluster=ci$RANDOM
export KUBECONFIG=$WSTMP/kubeconfig-$cluster
kind create cluster --name $cluster --wait 5m
function cleanup() {
    kind export logs --name $cluster $WSTMP/kindlogs || :
    kind delete cluster --name $cluster || :
    rm $KUBECONFIG
}
trap cleanup EXIT
kubectl cluster-info

PRE_LOAD_IMAGES=()
PRE_LOAD_IMAGES+=($(grep -e image: test-in-k8s.yaml | cut -d ':' -f 2- | xargs))
PRE_LOAD_IMAGES=+=(grep -h -e "^\s*image: .*$" src/test/resources/**/*.groovy | sed -e "s/^[[:space:]]*image: //" | sort | uniq | grep -v "windows" | grep -v "nonexistent" | grep -v "invalid")
for image in "${PRE_LOAD_IMAGES[@]}"
do
  docker pull $image
  kind load docker-image $image --name $cluster
done

bash test-in-k8s.sh
rm -rf $WSTMP/surefire-reports
kubectl cp jenkins:/checkout/target/surefire-reports $WSTMP/surefire-reports
