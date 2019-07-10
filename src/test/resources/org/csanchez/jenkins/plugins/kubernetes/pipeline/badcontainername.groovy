podTemplate(containers: [
        containerTemplate(name: 'badcontainerName_!', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node(POD_LABEL) {
      stage('Run') {
        container('busybox') {
          sh """
            will never run
          """
        }
      }
    }
}
