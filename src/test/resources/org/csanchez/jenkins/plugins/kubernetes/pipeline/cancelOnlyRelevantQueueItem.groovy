parallel k8s: {
  catchError(buildResult: 'SUCCESS') {
    podTemplate(yaml: '''
spec:
  containers:
  - name: jnlp
    image: some/invalid
''') {
      node(POD_LABEL) {
        sh 'false should never run'
      }
    }
  }
  echo 'cancelled pod item by now'
}, unrelated: {
  node('special-agent') {
    echo 'ran on special agent'
  }
}
