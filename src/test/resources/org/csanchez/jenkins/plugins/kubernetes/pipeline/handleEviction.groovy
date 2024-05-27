podTemplate {
  retry(count: 2, conditions: [kubernetesAgent()]) {
    node(POD_LABEL) {
      semaphore 'pod'
      sh 'sleep 1'
    }
  }
}
