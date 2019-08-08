podTemplate(label: 'runWithNonexistentDockerImage', volumes: [emptyDirVolume(mountPath: '/my-mount')], containers: [
        containerTemplate(name: 'pod-template-label-leave-only-room-for-singlesuffix', image: 'bogus-image', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runWithNonexistentDockerImage') {
        stage('Run') {
            container('pod-template-label-leave-only-room-for-singlesuffix') {
                sh 'echo "This message should never be displayed"'
            }
        }
    }
}