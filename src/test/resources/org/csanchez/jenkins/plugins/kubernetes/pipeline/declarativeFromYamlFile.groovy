pipeline {
  agent {
    kubernetes {
      label 'mypod'
      yamlFile 'declarativeYamlFile.yml'
    }
  }
  options {
    // Because there's no way for the container to actually get at the git repo on the disk of the box we're running on.
    skipDefaultCheckout(true)
  }
  stages {
    stage('Run maven') {
      steps {
        sh 'set'
        sh "echo OUTSIDE_CONTAINER_ENV_VAR = ${CONTAINER_ENV_VAR}"
        container('maven') {
          sh 'echo MAVEN_CONTAINER_ENV_VAR = ${CONTAINER_ENV_VAR}'
          sh 'mvn -version'
        }
        container('busybox') {
          sh 'echo BUSYBOX_CONTAINER_ENV_VAR = ${CONTAINER_ENV_VAR}'
          sh '/bin/busybox'
        }
      }
    }
  }
}
