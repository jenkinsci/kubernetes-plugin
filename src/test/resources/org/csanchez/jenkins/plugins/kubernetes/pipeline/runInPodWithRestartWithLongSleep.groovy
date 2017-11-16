package org.csanchez.jenkins.plugins.kubernetes.pipeline

podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('mypod') {
        stage('Run') {
            container('busybox') {
                    sh 'for i in `seq 1 10`; do echo $i; sleep 5; done'
            }
            echo 'finished the test!'
        }

    }
}
