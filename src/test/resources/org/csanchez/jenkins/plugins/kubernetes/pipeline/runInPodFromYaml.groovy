podTemplate(label: 'mypod', yaml: """
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
"""
) {

    node ('mypod') {
      stage('Run') {
        container('busybox') {
          sh """
            ## durable-task plugin generates a script.sh file.
            ##
            echo "script file: \$(find ../../.. -iname script.sh))"
            echo "script file contents: \$(find ../../.. -iname script.sh -exec cat {} \\;)"
            test -n "\$(cat \$(find ../../.. -iname script.sh))"
          """
        }
      }
    }
}