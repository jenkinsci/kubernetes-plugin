podTemplate(yaml: '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: kubectl
    image: bitnami/kubectl:1.25.4
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
