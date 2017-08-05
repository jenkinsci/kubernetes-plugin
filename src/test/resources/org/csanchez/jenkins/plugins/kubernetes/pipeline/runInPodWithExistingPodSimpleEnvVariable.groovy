node ('busybox') {

    stage('Run busybox') {
        container('busybox') {
            sh 'echo $POD_SIMPLE_ENV_VAR'
        }
    }
}
