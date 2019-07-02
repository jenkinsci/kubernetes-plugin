podTemplate(label: '$NAME', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node('$NAME') {
        container('busybox') {
            sh 'sleep 5; echo have started user process; sleep 999'
        }
    }
}
