package org.csanchez.jenkins.plugins.kubernetes.pipeline

podTemplate(label: 'label1 label2',
        containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    node ('label1') {
        container('busybox') {
                sh 'sleep 9999999'
        }
    }
}
