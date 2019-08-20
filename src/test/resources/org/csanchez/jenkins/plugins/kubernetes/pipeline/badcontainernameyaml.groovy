podTemplate(yaml: """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: badcontainername_!
    image: busybox
    command:
    - cat
    tty: true
  - name: badcontainername2_!
    image: busybox
    command:
    - cat
    tty: true
"""
) {

    node(POD_LABEL) {
      stage('Run') {
        container('busybox') {
          sh """
            will never run
          """
        }
      }
    }
}