podTemplate(yaml:'''
spec:
  containers:
  - name: stress-ng
    image: polinux/stress-ng
    command: ['sh', '-c', "sleep 30; stress-ng --vm 2 --timeout 30s -v"]
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
    sh 'sleep 120'
  }
}
