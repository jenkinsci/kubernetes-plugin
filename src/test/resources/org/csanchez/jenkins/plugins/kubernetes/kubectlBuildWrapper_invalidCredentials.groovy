podTemplate(yaml: '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: kubectl
    image: alpine/kubectl:1.34.1
    command:
      - sleep
    args:
      - infinity
'''
) {
  node(POD_LABEL) {
    container('kubectl') {
      kubeconfig(serverUrl:'url',credentialsId:'id') {
        sh 'kubectl version'
      }
    }
  }
}
