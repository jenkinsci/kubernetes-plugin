podTemplate(label: 'runInPodValidateOverrideSlaveConnectTimeout', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runInPodValidateOverrideSlaveConnectTimeout') {
        stage('Run') {
            container('busybox') {
                sh """
            echo "Hello from container!"
          """
            }
        }
    }
}
