podTemplate(yaml:'''
spec:
  containers:
  - name: stress-ng
    image: polinux/stress-ng
    command:
    - stress-ng
    args:
    - --vm
    - 2
    - --vm-bytes
    - 1G
    - --timeout
    - 10s
    - -v
    tty: true
    securityContext:
      runAsUser: 0
      privileged: true
    resources:
      limits:
        memory: "256Mi"
      requests:
        memory: "256Mi"
''') {
  node (POD_LABEL) {
    sh 'sleep 60'
  }
}
