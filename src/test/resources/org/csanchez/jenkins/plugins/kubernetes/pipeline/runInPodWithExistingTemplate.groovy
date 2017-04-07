node ('busybox') {
    sh 'echo outside container'

    stage('Run busybox') {
        container('busybox') {
            sh 'echo inside container'
        }
    }
}
