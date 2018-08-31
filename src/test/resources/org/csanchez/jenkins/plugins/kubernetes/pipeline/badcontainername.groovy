podTemplate(label: 'badcontainername', containers: [
        containerTemplate(name: 'badcontainerName_!', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('badcontainername') {
      stage('Run') {
        container('busybox') {
          sh """
            will never run
          """
        }
      }
    }
}
