podTemplate(label: 'mypod', podRetention: always(), containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('mypod') {
      stage('Run') {
        container('busybox') {
          sh """
            echo "Running pod with retention"
          """
        }
      }
    }
}