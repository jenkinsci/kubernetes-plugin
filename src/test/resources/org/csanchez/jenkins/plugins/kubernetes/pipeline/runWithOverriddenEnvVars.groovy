podTemplate(label: 'mypod',
        envVars: [
                envVar(key: 'POD_ENV_VAR', value: 'pod-env-var-value-first'),
                envVar(key: 'POD_ENV_VAR', value: 'pod-env-var-value')
        ],
        containers: [
                containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat',
                        envVars: [
                                envVar(key: 'HOME', value: '/root'),
                                envVar(key: 'POD_ENV_VAR', value: 'container-env-var-value-first'),
                                envVar(key: 'POD_ENV_VAR', value: 'container-env-var-value')
                        ],
                ),
        ]) {

    node ('mypod') {
        sh '''
        echo OUTSIDE_CONTAINER_HOME_ENV_VAR = $HOME
        echo OUTSIDE_CONTAINER_POD_ENV_VAR = $POD_ENV_VAR
        '''
        stage('Run busybox') {
            container('busybox') {
                sh '''
                echo inside container
                echo INSIDE_CONTAINER_HOME_ENV_VAR = $HOME
                echo INSIDE_CONTAINER_POD_ENV_VAR = $POD_ENV_VAR
                '''
            }
        }
    }
}
