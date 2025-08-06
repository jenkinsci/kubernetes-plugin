podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:windowsservercore-1809
  - name: shell
    image: mcr.microsoft.com/powershell:preview-windowsservercore-1809
    command:
    - powershell
    args:
    - Start-Sleep
    - 999999
  nodeSelector:
    kubernetes.io/os: windows
    node.kubernetes.io/windows-build: 10.0.17763
''') {
    node(POD_LABEL) {
        container('shell') {
            try {
                bat 'echo "starting ping" && ping 127.0.0.1 -n 3601 > test.txt'
            } catch (Exception e) { //If killing works we should be able to do things with test.txt
                bat 'rename test.txt test2.txt && echo "shut down gracefully"'
            }
        }
    }
}
