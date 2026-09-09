podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    resources:
      requests:
        cpu: 100m
        memory: 256Mi
      limits:
        cpu: 100m
        memory: 256Mi
  nodeSelector:
    disktype: special
''') {
    node(POD_LABEL) {
        sh 'true'
    }
}