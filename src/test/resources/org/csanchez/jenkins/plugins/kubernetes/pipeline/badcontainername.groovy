podTemplate(label: '$NAME', containers: [
        containerTemplate(name: 'badcontainerName_!', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('$NAME') {
      stage('Run') {
        container('busybox') {
          sh """
            will never run
          """
        }
      }
    }
}
