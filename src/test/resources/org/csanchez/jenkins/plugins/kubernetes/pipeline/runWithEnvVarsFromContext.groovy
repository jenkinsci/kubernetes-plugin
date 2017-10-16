podTemplate(label: 'mypod',
    containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    env.FROM_ENV_DEFINITION = "ABC"
    node ('mypod') {
        stage('Run busybox') {
            withEnv(['FROM_WITHENV_DEFINITION=DEF',
            'WITH_QUOTE="WITH_QUOTE',
            'AFTER_QUOTE=AFTER_QUOTE"',
            'ESCAPED_QUOTE=\\"ESCAPED_QUOTE',
            'AFTER_ESCAPED_QUOTE=AFTER_ESCAPED_QUOTE\\"'
            ]) {
                container('busybox') {
                    sh 'echo inside container'
                    sh """
                    echo The value of FROM_ENV_DEFINITION is \$FROM_ENV_DEFINITION
                    echo The value of FROM_WITHENV_DEFINITION is \$FROM_WITHENV_DEFINITION
                    echo The value of WITH_QUOTE is \$WITH_QUOTE
                    echo The value of AFTER_QUOTE is \$AFTER_QUOTE
                    echo The value of ESCAPED_QUOTE is \$ESCAPED_QUOTE
                    echo The value of AFTER_ESCAPED_QUOTE is \$AFTER_ESCAPED_QUOTE
                    """
                }
            }
        }
    }
}
