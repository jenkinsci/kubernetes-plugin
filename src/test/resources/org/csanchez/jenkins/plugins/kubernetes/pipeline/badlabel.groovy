podTemplate(label: 'mypod!123', yaml: """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: badcontainername
    image: busybox
    command:
    - cat
    tty: true
"""
) {

    node ('$NAME') {
      stage('Run') {
        container('busybox') {
          sh """
            will never run
          """
        }
      }
    }
}
