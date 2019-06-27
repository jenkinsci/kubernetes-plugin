podTemplate(label: 'manyLaunchers', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    node('manyLaunchers') {
        container('busybox') {
            branches = [:] // TODO cannot use collectEntries because: java.io.NotSerializableException: groovy.lang.IntRange
            for (int x = 0; x < 1000; x += 5) {
                def _x = x
                branches["sleep$x"] = {
                    sleep time: _x, unit: 'SECONDS'
                    sh 'sleep infinity'
                }
            }
            parallel branches
        }
    }
}
