podTemplate(label: 'runInPod', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node ('runInPod') {
        container('busybox') {
            sh 'sleep 9999999'
        }
    }
}
