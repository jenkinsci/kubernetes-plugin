podTemplate(label: 'runWithNonexistentDockerImageDoubleSuffix', volumes: [emptyDirVolume(mountPath: '/my-mount')], containers: [
        containerTemplate(name: 'bad-image', image: 'bogus-image', ttyEnabled: true, command: '/bin/cat'),
]) {

    node ('runWithNonexistentDockerImageDoubleSuffix') {
        stage('Run') {
            container('bad-image') {
                sh 'echo "This message should never be displayed"'
            }
        }
    }
}
