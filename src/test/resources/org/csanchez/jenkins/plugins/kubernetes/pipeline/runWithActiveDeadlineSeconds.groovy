podTemplate(label: '$NAME', activeDeadlineSeconds: 10, containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    semaphore 'podTemplate'
    node ('$NAME') {
        stage('Run') {
            container('busybox') {
                sh """
            echo "Hello from container!"
          """
            }
        }
    }
}
