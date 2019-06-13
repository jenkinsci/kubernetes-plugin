podTemplate(label: 'runWithCustomJnlpContainer', yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: cloudbees/jnlp-slave-with-java-build-tools
    args: ['$(JENKINS_SECRET)', '$(JENKINS_NAME)']
'''
) {
    node ('runWithCustomJnlpContainer') {
        sh 'hostname; mvn --version'
    }
}
