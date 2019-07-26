podTemplate(showRawYaml: false) { podTemplate(yaml: '''
apiVersion: v1
kind: Pod
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
'''
)
{
    node(POD_LABEL) {
        stage('Run') {
            container('busybox') {
                sh '''
                    echo "anything"   
                '''
            }
        }
    }
} }