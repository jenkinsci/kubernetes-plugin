podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:windowsservercore-1809
    env:
    - name: POD_ENV_VAR_FROM_SECRET
      valueFrom:
        secretKeyRef:
          key: password
          name: pod-secret
  - name: shell
    image: mcr.microsoft.com/powershell:preview-windowsservercore-1809
    command:
    - powershell
    args:
    - Start-Sleep
    - 999999
    env:
    - name: CONTAINER_ENV_VAR_FROM_SECRET
      valueFrom:
        secretKeyRef:
          key: password
          name: container-secret
  nodeSelector:
    kubernetes.io/os: windows
    node.kubernetes.io/windows-build: 10.0.17763
''') {
    node(POD_LABEL) {
        powershell 'echo "INSIDE_POD_ENV_VAR_FROM_SECRET = $Env:POD_ENV_VAR_FROM_SECRET or $($Env:POD_ENV_VAR_FROM_SECRET.ToUpper())"'
        container('shell') {
            powershell 'echo "INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = $Env:CONTAINER_ENV_VAR_FROM_SECRET or $($Env:CONTAINER_ENV_VAR_FROM_SECRET.ToUpper())"'
        }
    }
}
