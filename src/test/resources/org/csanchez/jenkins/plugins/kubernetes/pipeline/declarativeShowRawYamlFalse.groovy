pipeline {
  agent {
    kubernetes {
      showRawYaml false
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
'''
    }
  }
  stages {
    stage('Run') {
      steps {
        sh 'set'
        container('jnlp') {
          sh 'echo CONTAINER_ENV_VAR = ${CONTAINER_ENV_VAR}'
        }
      }
    }
  }
}
