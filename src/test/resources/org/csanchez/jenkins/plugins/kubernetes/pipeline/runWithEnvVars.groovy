podTemplate(label: 'mypod',
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
        containerTemplate(name: 'java7', image: 'openjdk:7u151-jre-alpine', ttyEnabled: true, command: '/bin/cat'),
        containerTemplate(name: 'java8', image: 'openjdk:8u151-jre-alpine', ttyEnabled: true, command: '/bin/cat')
    ]) {

    node ('mypod') {

        sh """
        echo OUTSIDE_CONTAINER_BUILD_NUMBER = \$BUILD_NUMBER
        echo OUTSIDE_CONTAINER_JOB_NAME = \$JOB_NAME
        echo OUTSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
        echo OUTSIDE_CONTAINER_ENV_VAR_LEGACY = \$CONTAINER_ENV_VAR_LEGACY
        echo OUTSIDE_CONTAINER_ENV_VAR_FROM_SECRET = \$CONTAINER_ENV_VAR_FROM_SECRET
        echo OUTSIDE_POD_ENV_VAR = \$POD_ENV_VAR
        echo OUTSIDE_POD_ENV_VAR_FROM_SECRET = \$POD_ENV_VAR_FROM_SECRET
        echo OUTSIDE_JAVA_HOME_X = \$JAVA_HOME_X
        echo OUTSIDE_GLOBAL = \$GLOBAL
        """
        stage('Run busybox') {
            container('busybox') {
                sh 'echo inside container'
                sh """
                echo INSIDE_CONTAINER_BUILD_NUMBER = \$BUILD_NUMBER
                echo INSIDE_CONTAINER_JOB_NAME = \$JOB_NAME
                echo INSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
                echo INSIDE_CONTAINER_ENV_VAR_LEGACY = \$CONTAINER_ENV_VAR_LEGACY
                echo INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = \$CONTAINER_ENV_VAR_FROM_SECRET
                echo INSIDE_POD_ENV_VAR = \$POD_ENV_VAR
                echo INSIDE_POD_ENV_VAR_FROM_SECRET = \$POD_ENV_VAR_FROM_SECRET
                echo INSIDE_JAVA_HOME_X = \$JAVA_HOME_X
                echo INSIDE_JAVA_HOME = \$JAVA_HOME
                echo INSIDE_GLOBAL = \$GLOBAL
                """
            }
            container('jnlp') {
                sh """
                echo JNLP_JAVA_HOME = \$JAVA_HOME
                echo JNLP_JAVA_HOME_X = \$JAVA_HOME_X
                """
            }
            container('java7') {
                sh """
                echo JAVA7_HOME = \$JAVA_HOME
                echo JAVA7_HOME_X = \$JAVA_HOME_X
                """
            }
            container('java8') {
                sh """
                echo JAVA8_HOME = \$JAVA_HOME
                echo JAVA8_HOME_X = \$JAVA_HOME_X
                """
            }
        }
    }
}
