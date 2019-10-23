podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins4eval/jnlp-agent:latest-windows
  nodeSelector:
    kubernetes.io/os: windows
'''
) {
    node(POD_LABEL) {
        bat 'dir'
    }
}
