podTemplate(cloud: 'kubernetes-plugin-test', label: 'mypod',
    envVars: [
        envVar(key: 'POD_ENV_VAR', value: 'pod-env-var-value'),
        secretEnvVar(key: 'POD_ENV_VAR_FROM_SECRET', secretName: 'pod-secret', secretKey: 'password')
    ],
    containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat',
            envVars: [
                containerEnvVar(key: 'CONTAINER_ENV_VAR_LEGACY', value: 'container-env-var-value'),
                envVar(key: 'CONTAINER_ENV_VAR', value: 'container-env-var-value'),
                secretEnvVar(key: 'CONTAINER_ENV_VAR_FROM_SECRET', secretName: 'container-secret', secretKey: 'password')
            ],
        ),
    ]) {

    node ('mypod') {
        sh """
        echo OUTSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
        echo OUTSIDE_CONTAINER_ENV_VAR_LEGACY = \$CONTAINER_ENV_VAR_LEGACY
        echo OUTSIDE_CONTAINER_ENV_VAR_FROM_SECRET = \$CONTAINER_ENV_VAR_FROM_SECRET
        echo OUTSIDE_POD_ENV_VAR = \$POD_ENV_VAR
        echo OUTSIDE_POD_ENV_VAR_FROM_SECRET = \$POD_ENV_VAR_FROM_SECRET
        """
        stage('Run busybox') {
            container('busybox') {
                sh 'echo inside container'
                sh """
                echo INSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
                echo INSIDE_CONTAINER_ENV_VAR_LEGACY = \$CONTAINER_ENV_VAR_LEGACY
                echo INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = \$CONTAINER_ENV_VAR_FROM_SECRET
                echo INSIDE_POD_ENV_VAR = \$POD_ENV_VAR
                echo INSIDE_POD_ENV_VAR_FROM_SECRET = \$POD_ENV_VAR_FROM_SECRET
                """
            }
        }
    }
}
