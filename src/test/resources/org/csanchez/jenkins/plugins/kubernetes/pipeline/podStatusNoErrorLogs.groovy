//noinspection GrPackage
pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: shell
    image: ubuntu
    command:
    - sleep
    args:
    - infinity
'''
        }
    }
    stages {
        stage('Run') {
            steps {
                sh 'hostname'
            }
        }
    }
}