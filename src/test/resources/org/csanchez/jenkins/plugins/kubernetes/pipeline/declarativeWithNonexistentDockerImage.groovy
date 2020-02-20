pipeline {
  agent {
    kubernetes {
      label "somelabel-${UUID.randomUUID().toString()}"
      yaml """
metadata:
  labels:
    some-label: some-label-value
    class: KubernetesDeclarativeAgentTest
spec:
  containers:
  - name: jnlp
    env:
    - name: CONTAINER_ENV_VAR
      value: jnlp
  - name: container1
    image: nonexistent-docker-image
    command:
    - cat
    tty: true
  - name: maven
    image: maven:3.3.9-jdk-8-alpine
    command:
    - cat
    tty: true
    env:
    - name: CONTAINER_ENV_VAR
      value: maven
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