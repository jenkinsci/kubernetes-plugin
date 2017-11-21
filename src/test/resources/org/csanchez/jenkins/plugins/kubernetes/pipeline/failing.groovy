podTemplate(label: 'failing', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('failing') {
      stage('Run') {
        container('busybox') {
          sh """
            echo will fail
            false
          """
        }
      }
    }
}
