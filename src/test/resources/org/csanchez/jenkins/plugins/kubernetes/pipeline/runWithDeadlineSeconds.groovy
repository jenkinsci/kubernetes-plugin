podTemplate(label: 'deadline', deadlineSeconds: 10, containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('deadline') {
        stage('Run') {
            container('busybox') {
                sh """
            echo "Hello from container!"
          """
            }
        }
    }
}
