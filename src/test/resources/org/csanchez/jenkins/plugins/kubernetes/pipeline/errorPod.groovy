podTemplate(yaml:'''
spec:
  containers:
  - name: jnlp
    command:
    - 'sh'
    - '-ec'
    - 'echo Foo; exit 1'
''') {
  node(POD_LABEL) {
    sh 'true'
  }
}
