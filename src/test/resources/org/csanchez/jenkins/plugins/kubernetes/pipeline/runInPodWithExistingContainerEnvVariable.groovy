node ('busybox') {

    stage('Run busybox') {
        container('busybox') {
            sh 'echo $ENV_VAR'
        }
    }
}
