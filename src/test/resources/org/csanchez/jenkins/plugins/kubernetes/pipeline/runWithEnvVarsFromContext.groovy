podTemplate(label: 'mypod',
    containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    env.FROM_ENV_DEFINITION = "ABC"
    node ('mypod') {
        stage('Run busybox') {
            withEnv(['FROM_WITHENV_DEFINITION=DEF']) {
                container('busybox') {
                    sh 'echo inside container'
                    sh """
                    echo The value of FROM_ENV_DEFINITION is \$FROM_ENV_DEFINITION
                    echo The value of FROM_WITHENV_DEFINITION is \$FROM_WITHENV_DEFINITION
                    echo
                    """
                }
            }
        }
    }
}
