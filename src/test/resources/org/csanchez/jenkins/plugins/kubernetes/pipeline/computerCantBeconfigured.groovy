podTemplate(label: 'runInPod', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node('runInPod') {
        semaphore 'pod'
    }
}
