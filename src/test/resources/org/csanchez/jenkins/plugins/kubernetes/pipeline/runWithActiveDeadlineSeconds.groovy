podTemplate(label: 'runWithActiveDeadlineSeconds', activeDeadlineSeconds: 10, containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    semaphore 'podTemplate'
    node ('runWithActiveDeadlineSeconds') {
        stage('Run') {
            container('busybox') {
                sh """
            echo "Hello from container!"
          """
            }
        }
    }
}
