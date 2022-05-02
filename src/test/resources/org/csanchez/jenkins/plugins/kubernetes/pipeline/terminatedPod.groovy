podTemplate(yaml: '''
spec:
  containers:
  - name: busybox
    image: busybox
    command:
    - sleep
    - 99d
  terminationGracePeriodSeconds: 3
''') {
  retry(count: 2, errorConditions: [kubernetesAgent()]) {
    node(POD_LABEL) {
        container('busybox') {
            sh 'echo hello world'
            sh 'sleep 15'
        }
    }
  }
}
