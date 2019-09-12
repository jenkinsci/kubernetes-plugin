podTemplate(containers: [
        containerTemplate(name: 'jnlp', image: 'jenkinsci/jnlp-slave:latest', workingDir: '/home/jenkins'),
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat')
]) {
    node (POD_LABEL) {
      stage('Run') {
        sh 'env | sort'
        container('busybox') {
          sh 'env | sort'
        }
      }
    }
}
