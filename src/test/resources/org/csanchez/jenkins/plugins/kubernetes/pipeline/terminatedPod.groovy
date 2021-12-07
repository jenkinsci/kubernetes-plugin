podTemplate(label: '$NAME', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node ('$NAME') {
        container('busybox') {
            sh 'echo hello world'
            sh 'sleep 9999999'
        }
    }
}
