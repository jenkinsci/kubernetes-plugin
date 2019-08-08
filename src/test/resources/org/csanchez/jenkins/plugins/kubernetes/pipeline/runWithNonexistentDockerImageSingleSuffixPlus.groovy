podTemplate(label: 'runWithNonexistentDockerImage', volumes: [emptyDirVolume(mountPath: '/my-mount')], containers: [
        containerTemplate(name: 'pod-label-leave-room-for-single-suffix-plus-extra', image: 'bogus-image', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runWithNonexistentDockerImage') {
        stage('Run') {
            container('pod-label-leave-room-for-single-suffix-plus-extra') {
                sh 'echo "This message should never be displayed"'
            }
        }
    }
}