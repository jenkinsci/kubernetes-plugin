package org.csanchez.jenkins.plugins.kubernetes.pipeline

podTemplate(label: 'mypod', namespace: 'default', idleMinutes: 0, containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('mypod') {
        stage('Run') {
            container('busybox') {
                    semaphore 'wait'
                    sh 'for i in `seq 1 20`; do echo $i; sleep 5; done'
                    sh 'ps'
            }
            container('busybox') {
                sh 'ps'
            }
            echo 'finished the test!'
        }

    }
}
