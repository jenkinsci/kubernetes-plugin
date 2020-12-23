pipeline {
  agent {
    kubernetes {
      label 'multiple labels'
      containerTemplate {
        name 'maven'
        image 'maven:3.3.9-jdk-8-alpine'
        command 'sleep'
        args '9999999'
      }
      podRetention onFailure()
    }
  }
  environment {
    CONTAINER_ENV_VAR = 'container-env-var-value'
  }
  stages {
    stage('Run maven') {
      steps {
        sh 'set'
        sh 'test -f /usr/bin/mvn' // checking backwards compatibility
        sh "echo OUTSIDE_CONTAINER_ENV_VAR = ${CONTAINER_ENV_VAR}"
        container('maven') {
          sh 'echo INSIDE_CONTAINER_ENV_VAR = ${CONTAINER_ENV_VAR}'
          sh 'mvn -version'
        }
      }
    }
	stage('Run maven with a different shell') {
		steps {
		  container(name: 'maven', shell: '/bin/bash') {
			sh 'mvn -version'
		  }
		}
	  }
  }
}
