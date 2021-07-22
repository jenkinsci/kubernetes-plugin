podTemplate(slaveConnectTimeout:10, yaml:'''
  spec:
    containers:
    - name: jnlp
      readinessProbe:
        exec:
          command:
          - cat
          - /tmp/healthy
        initialDelaySeconds: 1
        periodSeconds: 1
''') {
  node(POD_LABEL) {
    sh 'true'
  }
}
