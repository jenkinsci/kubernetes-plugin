pipeline {
  agent {
    kubernetes {
      containerTemplate {
        name 'busybox'
        image 'busybox'
        workingDir '/home/jenkins/agent'
        command 'sleep'
        args 'infinity'
      }
    }
  }
  stages{
      stage('Build') {
          steps {

              sh 'echo DEFAULT_BUILD_NUMBER: ${BUILD_NUMBER}'
              container('jnlp')
              {
                  sh 'echo JNLP_BUILD_NUMBER: ${BUILD_NUMBER}'
              }
              container('busybox'){
                  sh 'echo BUSYBOX_BUILD_NUMBER: ${BUILD_NUMBER}'
              }
          }
      }
  }
}
