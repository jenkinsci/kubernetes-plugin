podTemplate(label: '$NAME', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node('$NAME') {
        semaphore 'pod'
    }
}
