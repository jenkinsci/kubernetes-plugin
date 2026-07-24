podTemplate(yaml: '''
spec:
  containers:
  - name: golang
    image: golang:1.6.3-alpine
    command:
    - sleep
    args:
    - infinity
''') {
  node(POD_LABEL) {
    container('golang') {
      sh 'go version'
    }
  }
}
