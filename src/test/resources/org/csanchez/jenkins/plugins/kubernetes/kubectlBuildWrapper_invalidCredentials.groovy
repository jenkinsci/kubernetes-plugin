podTemplate(yaml: '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: kubectl
    image: bitnami/kubectl:1.16.3
    command:
    - cat
    tty: true
'''
) {
  node(POD_LABEL) {
    container('kubectl') {
      kubectl(serverUrl:'url',credentialsId:'id') {
        sh 'kubectl version'
      }
    }
  }
}
