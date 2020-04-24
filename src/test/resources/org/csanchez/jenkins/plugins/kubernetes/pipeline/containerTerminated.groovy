podTemplate(yaml:'''
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:4.3-4 # alpine flavor has alpine 3.9 which doesn't have stress-ng
    securityContext:
      runAsUser: 0
      privileged: true
    resources:
      limits:
        memory: "256Mi"
      requests:
        memory: "256Mi"
''') {
  node(POD_LABEL) {
    sh '''
        apt-get update
        apt-get install -y stress-ng
        stress-ng --vm 2 --vm-bytes 1G  --timeout 30s -v
      '''
  }
}
