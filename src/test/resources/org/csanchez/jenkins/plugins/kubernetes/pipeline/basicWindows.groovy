podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:windowsservercore-1809
  nodeSelector:
    beta.kubernetes.io/os: windows
'''
) {
    node(POD_LABEL) {
        bat 'dir'
        powershell 'Get-ChildItem Env: | Sort Name'
    }
}
