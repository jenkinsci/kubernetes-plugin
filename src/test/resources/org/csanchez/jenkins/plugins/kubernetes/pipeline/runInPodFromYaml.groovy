podTemplate(yaml: """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: busybox
    image: busybox
    command:
    - cat
    tty: true
    env:
    - name: CONTAINER_ENV_VAR
      value: container-env-var-value
    - name: CONTAINER_ENV_VAR_FROM_SECRET
      valueFrom:
        secretKeyRef:
          key: password
          name: container-secret
"""
) {

    node(POD_LABEL) {
      stage('Run') {
        container('busybox') {
            // see runInPod.groovy
            sh '''set +x
            echo "script file: $(find ../../.. -path '*@tmp/durable-*/script.sh'))"
            echo "script file contents: $(find ../../.. -path '*@tmp/durable-*/script.sh' -exec cat {} ';')"
            echo INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = $CONTAINER_ENV_VAR_FROM_SECRET or `echo $CONTAINER_ENV_VAR_FROM_SECRET | tr [a-z] [A-Z]`
            test -n "$(cat "$(find ../../.. -path '*@tmp/durable-*/script.sh')")"
            '''
        }
      }
    }
}
