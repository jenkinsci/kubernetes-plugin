podTemplate(agentContainer:'foo',
            agentInjection: true,
            yaml:'''
spec:
  containers:
  - name: foo
    image: busybox
''') {
  node(POD_LABEL) {
    sh 'true'
  }
}
