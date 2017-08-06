node ('busybox') {
    sh 'echo outside container'

    sh """
    echo OUTSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
    echo OUTSIDE_POD_ENV_VAR = \$POD_ENV_VAR
    """

    stage('Run busybox') {
        container('busybox') {
            sh 'echo inside container'
            sh """
            echo INSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
            echo INSIDE_POD_ENV_VAR = \$POD_ENV_VAR
            """
        }
    }
}
