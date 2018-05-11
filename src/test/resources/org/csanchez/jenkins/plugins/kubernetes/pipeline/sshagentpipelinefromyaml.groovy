pipeline {
  agent {
    kubernetes {
      label "sshagent"
      yaml """
      apiVersion: v1
      kind: Pod
      metadata:
      spec:
        containers:
        - name: ssh-client
          image: kroniak/ssh-client:3.6
          command:
          - cat
          tty: true
      """
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
