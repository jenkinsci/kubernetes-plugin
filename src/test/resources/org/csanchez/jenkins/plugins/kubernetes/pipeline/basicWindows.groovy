podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/jnlp-agent:latest-windows
  nodeSelector:
    beta.kubernetes.io/os: windows
'''
) {
    node(POD_LABEL) {
        bat 'dir'
    }
}
