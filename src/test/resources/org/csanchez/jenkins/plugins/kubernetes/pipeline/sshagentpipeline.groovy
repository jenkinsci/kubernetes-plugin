pipeline {
  agent {
    kubernetes {
      label "sshagent"
      containerTemplate {
        name 'ssh-client'
        image 'kroniak/ssh-client:3.6'
        workingDir '/home/jenkins'
        ttyEnabled true
        command 'cat'
      }
    }
  }


    stages{
        stage('container log') {
            steps{
                container('ssh-client') {
                    sshagent (credentials: ['ContainerExecDecoratorPipelineTest-sshagent']) {
                        sh 'env'
                        sh 'ssh-add -L'
                        sh 'ssh -vT -o "StrictHostKeyChecking=no" git@github.com || exit 0'
                    }
                }
                sshagent (credentials: ['ContainerExecDecoratorPipelineTest-sshagent']) {
                    container('ssh-client') {
                        sh 'env'
                        sh 'ssh-add -L'
                        sh 'ssh -vT -o "StrictHostKeyChecking=no" git@github.com || exit 0'
                    }
                }
            }
        }
    }
}
