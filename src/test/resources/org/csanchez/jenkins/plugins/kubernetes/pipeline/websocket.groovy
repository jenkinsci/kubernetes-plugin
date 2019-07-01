podTemplate(label: 'websocket', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node('websocket') {
        container('busybox') {
            sh 'sleep 5; echo have started user process; sleep 999'
        }
    }
}
