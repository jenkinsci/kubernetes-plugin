pipeline {
  agent {
    kubernetes {
      cloud 'minikube'
      label 'mypod'
      containerTemplate {
        name 'maven'
        image 'maven:3.3.9-jdk-8-alpine'
        ttyEnabled true
        command 'cat'
      }
    }
  }
  stages {
    stage('Run maven') {
      steps {
        sh 'mvn -version'
      }
    }
  }
}
