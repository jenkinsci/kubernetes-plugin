podTemplate(label: 'runJobWithSpaces', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runJobWithSpaces') {
        stage('Run') {
            container('busybox') {
                sh 'echo "pwd is -$(pwd)-"'
            }
        }

    }
}
