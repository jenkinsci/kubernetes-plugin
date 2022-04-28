podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:windowsservercore-1809
  nodeSelector:
    kubernetes.io/os: windows
    node.kubernetes.io/windows-build: 10.0.17763
'''
) {
    node(POD_LABEL) {
        bat 'dir'
        powershell 'Get-ChildItem Env: | Sort Name'
    }
}
