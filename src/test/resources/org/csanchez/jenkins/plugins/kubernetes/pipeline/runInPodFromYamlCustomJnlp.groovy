podTemplate(label: 'runInPodFromYamlCustomJnlp', yaml: """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: 'jenkins/jnlp-slave:3.27-1-alpine'
"""
) {

    node ('runInPodFromYamlCustomJnlp') {
      stage('Run') {
        sh 'echo running'
      }
    }
}
