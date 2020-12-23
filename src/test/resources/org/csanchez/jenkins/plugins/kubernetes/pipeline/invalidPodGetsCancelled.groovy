podTemplate(yaml: '''
spec:
  containers:
  - name: invalid-container
''') {
  node(POD_LABEL) {
    sh 'This will never run'
  }
}
