pipeline {
  agent {
    kubernetes {
      label "reallylongcontainer123-${UUID.randomUUID().toString()}"
      yaml """
metadata:
  labels:
    some-label: some-label-value
    class: KubernetesDeclarativeAgentTest
spec:
  containers:
  - name: container1
    image: nonexistent-docker-image
    command:
    - cat
    tty: true
"""
     }
  }
  stages {
    stage('Run') {
      steps {
        container('container1') {
          sh """
        will never run
          """
        }
      }
    }
  }
}