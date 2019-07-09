/**
 * This pipeline will execute a simple Maven build using a pod template in yaml
 */

podTemplate(yaml: """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: maven
    image: maven:3.3.9-jdk-8-alpine
    command:
    - cat
    tty: true
    env:
    - name: CONTAINER_ENV_VAR
      value: container-env-var-value
"""
  ) {

  node(POD_LABEL) {
    stage('Build a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      container('maven') {
          sh 'mvn -B clean package'
      }
    }
  }
}
