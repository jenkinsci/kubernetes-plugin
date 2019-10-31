podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/jnlp-agent:latest-windows
  - name: shell
    image: mcr.microsoft.com/powershell:preview-windowsservercore-1809
    command:
    - powershell
    args:
    - Start-Sleep
    - 999999
  nodeSelector:
    beta.kubernetes.io/os: windows
''') {
    node(POD_LABEL) {
        container('shell') {
            powershell 'for ($i = 0; $i -lt 10; $i++) {echo "sleeping #$i"; Start-Sleep 5}'
        }
        echo 'finished the test!'
    }
}
