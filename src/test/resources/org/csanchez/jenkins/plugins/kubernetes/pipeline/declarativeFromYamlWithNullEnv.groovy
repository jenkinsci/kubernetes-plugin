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
    command:
    - cat
    tty: true
    env:
'''
    }
  }
  stages {
    stage('Run in JNLP container') {
      steps {
        container('jnlp') {
          sh '''
            if [ "$CONTAINER_ENV_VAR" = jnlp ]; then
              echo jnlp container: OK
            else
              echo "jnlp container: CONTAINER_ENV_VAR='$CONTAINER_ENV_VAR'"
              exit 1
            fi
          '''
        }
      }
    }
    stage('Run in default container') {
      steps {
        sh '''
          if [ -z "${CONTAINER_ENV_VAR+defined}" ]; then
            echo default container: OK
          else
            echo "default container: CONTAINER_ENV_VAR='$CONTAINER_ENV_VAR'"
            exit 1
          fi
        '''
      }
    }
  }
}
