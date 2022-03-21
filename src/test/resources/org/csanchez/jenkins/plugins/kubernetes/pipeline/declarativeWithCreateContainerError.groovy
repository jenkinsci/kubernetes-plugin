pipeline {
  agent {
    kubernetes {
      yaml '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: busybox
    image: busybox
    tty: true
    command: ['sh', '-c', "thiscommandshouldcreateanerror;"]
'''
     }
  }
  stages {
    stage('Run') {
      steps {
        container('busybox') {
          sh """
        will never run
          """
        }
      }
    }
  }
}
