pipeline {
  agent {
    kubernetes {
      label 'declarative-custom-workspace-pod'
      customWorkspace 'some/other/path'
      defaultContainer 'maven'
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
    stage('Run maven') {
      steps {
        sh 'mvn -version'
        sh "echo Workspace dir is ${pwd()}"
      }
    }
  }
}
