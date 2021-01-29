podTemplate(slaveConnectTimeout:10, yaml:'''
  spec:
    containers:
    - name: neverready
      image: busybox
      command: ['sleep', '99999999']
      readinessProbe:
        exec:
          command:
          - cat
          - /tmp/healthy
        initialDelaySeconds: 5
        periodSeconds: 5
''') {
  node(POD_LABEL) {
    sh 'true'
  }
}
