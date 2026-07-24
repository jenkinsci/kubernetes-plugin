podTemplate(label: '$NAME-1', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    semaphore 'podTemplate1'
    node ('$NAME-1') {
        semaphore 'pod1'
        stage('Run') {
            container('busybox') {
                // see runInPod.groovy
                sh '''
                    echo "script file: $(find ../../.. -path '*@tmp/durable-*/script.sh'))"
                    echo "script file contents: $(find ../../.. -path '*@tmp/durable-*/script.sh' -exec cat {} ';')"
                    test -n "$(cat "$(find ../../.. -path '*@tmp/durable-*/script.sh')")"
                '''
            }
        }
    }
}

podTemplate(label: '$NAME-2', containers: [
        containerTemplate(name: 'busybox2', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    semaphore 'podTemplate2'
    node ('$NAME-2') {
        semaphore 'pod2'
        stage('Run') {
            container('busybox2') {
                sh '''
                    echo "script file: $(find ../../.. -path '*@tmp/durable-*/script.sh'))"
                    echo "script file contents: $(find ../../.. -path '*@tmp/durable-*/script.sh' -exec cat {} ';')"
                    test -n "$(cat "$(find ../../.. -path '*@tmp/durable-*/script.sh')")"
                '''
            }
        }
    }
}
