pipeline {
  agent {
    kubernetes {
      yaml '''
spec:
  containers:
  - name: busybox
    image: busybox
    command:
    - sleep
    - 99d
  terminationGracePeriodSeconds: 3
      '''
      defaultContainer 'busybox'
      retries 2
    }
  }
  stages {
    stage('Run') {
      steps {
        sh 'echo hello world'
        sh 'sleep 15'
      }
    }
  }
}
