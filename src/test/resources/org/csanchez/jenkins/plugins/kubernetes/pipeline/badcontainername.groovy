podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'badcontainerName_!', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('mypod') {
      stage('Run') {
        container('busybox') {
          sh """
            will never run
          """
        }
      }
    }
}
