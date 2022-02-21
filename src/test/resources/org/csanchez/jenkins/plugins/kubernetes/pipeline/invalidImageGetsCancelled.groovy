podTemplate(yaml: '''
spec:
  containers:
  - name: jnlp
    image: some/invalid
''') {
  node(POD_LABEL) {
    sh 'This will never run'
  }
}
