This contains the setup to run `mvn test` locally and provisioning build pods in a locally running Kind cluster.

It's basically the same than `test-in-k8s.sh` but the Jenkins instance runs on the host instead of a Kind pod.

`dockerhost.yaml` is a trick to allow pods to contact the Jenkins instance which runs on the host.

Usage:
```shell
./test-local.sh KubernetesPipelineRJRTest#basicPipeline
```

Note that this requires a locally running Kind cluster (defaults are ok, just `kind create cluster`).