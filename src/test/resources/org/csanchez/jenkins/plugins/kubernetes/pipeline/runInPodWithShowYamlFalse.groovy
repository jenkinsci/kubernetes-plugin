podTemplate(label: 'runInPodWithShowRawYamlFalse', showRawYaml: false,  yaml: """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: busybox
    image: busybox
    command:
    - cat
    tty: true
    env:
    - name: CONTAINER_ENV_VAR
      value: container-env-var-value
    - name: CONTAINER_ENV_VAR_FROM_SECRET
      valueFrom:
        secretKeyRef:
          key: password
          name: container-secret
"""
) {

    node ('runInPodWithShowRawYamlFalse') {
        stage('Run') {
            container('busybox') {
                sh '''
                    echo "anything"   
                '''
            }
        }
    }
}