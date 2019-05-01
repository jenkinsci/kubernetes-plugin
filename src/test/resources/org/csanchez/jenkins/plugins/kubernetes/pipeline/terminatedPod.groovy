podTemplate(label: 'terminatedPod', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node ('terminatedPod') {
        container('busybox') {
            sh 'sleep 9999999'
        }
    }
}
