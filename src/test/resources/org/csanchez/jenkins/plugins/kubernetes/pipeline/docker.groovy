pipeline {
  agent {
    kubernetes {
      containerTemplate {
        name 'docker'
        image 'docker:1.11'
        ttyEnabled true
        command 'cat'
      }
    }
  }
  stages {
    stage('Run maven') {
        steps {
            container('docker') {
                withDockerRegistry(registry: [credentialsId: 'ContainerExecDecoratorPipelineTest-docker']) {
                    sh 'hostname'
                }
            }
        }
    }
  }
}
