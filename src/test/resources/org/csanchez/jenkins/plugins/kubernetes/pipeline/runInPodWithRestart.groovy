package org.csanchez.jenkins.plugins.kubernetes.pipeline

podTemplate(label: 'runInPodWithRestart', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runInPodWithRestart') {
        stage('Run') {
            container('busybox') {
                sh 'mkdir hz'
                sh 'echo "initpwd is -$(pwd)-"'
                dir('hz') {
                    sh 'echo "dirpwd is -$(pwd)-"'
                }
                sh 'echo "postpwd is -$(pwd)-"'
            }
        }

    }
}
