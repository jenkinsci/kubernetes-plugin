pipeline {
    agent {
        kubernetes {
            defaultContainer 'maven'
            yaml '''
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
    workingDir: /home/jenkins/wsp1
    command:
    - cat
    tty: true
    env:
    - name: CONTAINER_ENV_VAR
      value: maven
'''
        }
    }

    stages {
        stage('Run maven') {
            steps {
                dir('foo') {
                    container('jnlp') {
                        sh 'echo [jnlp] current dir is $(pwd)'
                        sh 'echo [jnlp] WORKSPACE=$WORKSPACE'
                    }
                    container('maven') {
                        sh 'mvn -version'
                        sh 'echo [maven] current dir is $(pwd)'
                        sh 'echo [maven] WORKSPACE=$WORKSPACE'
                    }
                    sh 'echo [default:maven] current dir is $(pwd)'
                    sh 'echo [default:maven] WORKSPACE=$WORKSPACE'
                }
            }
        }
    }
}
