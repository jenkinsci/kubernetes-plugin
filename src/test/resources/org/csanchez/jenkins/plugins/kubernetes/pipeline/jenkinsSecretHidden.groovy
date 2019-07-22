podTemplate(containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    node (POD_LABEL) {
      semaphore 'pod'
      stage('Run') {
        container('busybox') {
          sh "true"
        }
      }
    }
}
