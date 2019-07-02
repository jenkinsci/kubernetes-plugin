podTemplate(label: '$NAME', podRetention: always(), containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('$NAME') {
      stage('Run') {
        container('busybox') {
          sh """
            echo "Running pod with retention"
          """
        }
      }
    }
}