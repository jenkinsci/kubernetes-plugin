podTemplate(label: 'runInPodFromYaml', yaml: """
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

    node ('runInPodFromYaml') {
      stage('Run') {
        container('busybox') {
            sh '''set +x
            ## durable-task plugin generates a script.sh file.
            ##
            echo "script file: $(find ../../.. -iname script.sh))"
            echo "script file contents: $(find ../../.. -iname script.sh -exec cat {} \\;)"
            echo INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = $CONTAINER_ENV_VAR_FROM_SECRET or `echo $CONTAINER_ENV_VAR_FROM_SECRET | tr [a-z] [A-Z]`
            test -n "$(cat "$(find ../../.. -iname script.sh)")"
            '''
        }
      }
    }
}
