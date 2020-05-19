package org.csanchez.jenkins.plugins.kubernetes.pipeline

podTemplate(containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    node (POD_LABEL) {
        container('busybox') {
                sh 'sleep 9999999'
        }
    }
}
