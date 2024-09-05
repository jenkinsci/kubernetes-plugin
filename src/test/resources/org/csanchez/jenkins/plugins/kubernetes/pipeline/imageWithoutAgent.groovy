podTemplate(agentContainer:'foo',
            agentInjection: true,
            yaml:'''
spec:
  containers:
  - name: foo
    image: eclipse-temurin:22.0.2_9-jre
''') {
  node(POD_LABEL) {
    sh 'true'
  }
}
