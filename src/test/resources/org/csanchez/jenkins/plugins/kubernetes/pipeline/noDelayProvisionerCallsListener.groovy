podTemplate(containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node (POD_LABEL) {
      stage('Run') {
        container('busybox') {
          sh 'echo foo'
        }
      }
    }
}
