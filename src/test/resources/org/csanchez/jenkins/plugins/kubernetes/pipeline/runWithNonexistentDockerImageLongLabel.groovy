podTemplate(label: 'runWithNonexistentDockerImage', volumes: [emptyDirVolume(mountPath: '/my-mount')], containers: [
        containerTemplate(name: 'long-pod-template-label-leaves-no-room-for-even-single-suffix', image: 'bogus-image', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runWithNonexistentDockerImage') {
        stage('Run') {
            container('long-pod-template-label-leaves-no-room-for-even-single-suffix') {
                sh 'echo "This message should never be displayed"'
            }
        }
    }
}