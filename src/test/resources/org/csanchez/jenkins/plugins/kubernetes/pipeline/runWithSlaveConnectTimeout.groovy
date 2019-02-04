podTemplate(label: 'runWithSlaveConnectTimeout', slaveConnectTimeout: 10, containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runWithSlaveConnectTimeout') {
        stage('Run') {
            container('busybox') {
                sh """
            echo "Hello from container!"
          """
            }
        }
    }
}
