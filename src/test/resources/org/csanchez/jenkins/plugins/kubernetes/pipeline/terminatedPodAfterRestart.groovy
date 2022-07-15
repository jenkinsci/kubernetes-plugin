package org.csanchez.jenkins.plugins.kubernetes.pipeline

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
  retry(count: 2, conditions: [kubernetesAgent()]) {
    node(POD_LABEL) {
        container('busybox') {
            sh 'sleep 15'
        }
    }
  }
}
