node ('busybox') {

    stage('Run busybox') {
        container('busybox') {
            sh 'echo $POD_ENV_VAR_FROM_SECRET'
        }
    }
}
