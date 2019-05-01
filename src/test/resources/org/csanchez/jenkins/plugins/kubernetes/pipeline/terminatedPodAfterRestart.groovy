package org.csanchez.jenkins.plugins.kubernetes.pipeline

podTemplate(label: 'terminatedPodAfterRestart', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    node ('terminatedPodAfterRestart') {
        container('busybox') {
                sh 'sleep 9999999'
        }
    }
}
