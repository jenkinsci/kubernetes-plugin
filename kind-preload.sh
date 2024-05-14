#!/usr/bin/env bash
set -euxo pipefail
cd $(dirname $0)

PRE_LOAD_IMAGES=()
PRE_LOAD_IMAGES+=($(grep -e image: test-in-k8s.yaml | cut -d ':' -f 2- | xargs))
PRE_LOAD_IMAGES+=($(grep -h --include="*.groovy" -e "^\s*image: .*$" -R src/test/resources | sed -e "s/^[[:space:]]*image: //" | sort | uniq | grep -v "windows" | grep -v "nonexistent" | grep -v "invalid" | xargs))
PRE_LOAD_IMAGES+=($(grep -e FROM src/main/resources/org/csanchez/jenkins/plugins/kubernetes/Dockerfile | cut -d ' ' -f 2-))
if [[ -v cluster ]]
then
  name_arg="--name $cluster"
else
  name_arg=
fi
for image in "${PRE_LOAD_IMAGES[@]}"
do
  docker pull "$image"
  kind load docker-image "$image" $name_arg
done
