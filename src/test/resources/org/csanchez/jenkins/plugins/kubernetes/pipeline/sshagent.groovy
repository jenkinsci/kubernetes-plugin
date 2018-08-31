podTemplate(label: 'sshagent', containers: [
        containerTemplate(name: 'ssh-client', image: 'kroniak/ssh-client:3.6', ttyEnabled: true, command: 'cat')
]) {
    node ('sshagent') {
        stage('container log') {
            container('ssh-client') {
                sshagent (credentials: ['ContainerExecDecoratorPipelineTest-sshagent']) {
                    sh 'env'
                    sh 'ssh-add -L'
                    sh 'ssh -vT -o "StrictHostKeyChecking=no" git@github.com'
                }
            }
        }
    }
}
