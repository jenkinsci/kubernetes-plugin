pipeline {
    agent {
        kubernetes {
            defaultContainer 'shell'
            yaml '''
spec:
  containers:
  - name: shell
    image: ubuntu
    workingDir: /else$where
    command:
    - sleep
    args:
    - infinity
'''
        }
    }
    stages {
        stage('run') {
            steps {
                sh 'echo running in container in "$PWD"'
            }
        }
    }
}
