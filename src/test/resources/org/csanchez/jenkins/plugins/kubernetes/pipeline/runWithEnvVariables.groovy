podTemplate(
    envVars: [
        envVar(key: 'POD_ENV_VAR', value: 'pod-env-var-value'),
        secretEnvVar(key: 'POD_ENV_VAR_FROM_SECRET', secretName: 'pod-secret', secretKey: 'password'),
        secretEnvVar(key: 'EMPTY_POD_ENV_VAR_FROM_SECRET', secretName: 'empty-secret', secretKey: 'password')
    ],
    containers: [
        containerTemplate(name: 'busybox', image: 'busybox', command: 'sleep infinity',
            envVars: [
                containerEnvVar(key: 'CONTAINER_ENV_VAR_LEGACY', value: 'container-env-var-value'),
                envVar(key: 'CONTAINER_ENV_VAR', value: 'container-env-var-value'),
                secretEnvVar(key: 'CONTAINER_ENV_VAR_FROM_SECRET', secretName: 'container-secret', secretKey: 'password')
            ],
        ),
        containerTemplate(name: 'java17', image: 'eclipse-temurin:17-jre-ubi9-minimal', command: 'sleep infinity'),
        containerTemplate(name: 'java21', image: 'eclipse-temurin:21-jre-ubi9-minimal', command: 'sleep infinity')
    ]) {

    node(POD_LABEL) {

        sh '''set +x
        echo OUTSIDE_CONTAINER_BUILD_NUMBER = $BUILD_NUMBER
        echo OUTSIDE_CONTAINER_JOB_NAME = $JOB_NAME
        echo OUTSIDE_CONTAINER_ENV_VAR = $CONTAINER_ENV_VAR
        echo OUTSIDE_CONTAINER_ENV_VAR_LEGACY = $CONTAINER_ENV_VAR_LEGACY
        echo OUTSIDE_CONTAINER_ENV_VAR_FROM_SECRET = $CONTAINER_ENV_VAR_FROM_SECRET or `echo $CONTAINER_ENV_VAR_FROM_SECRET | tr [a-z] [A-Z]`
        echo OUTSIDE_POD_ENV_VAR = $POD_ENV_VAR
        echo OUTSIDE_POD_ENV_VAR_FROM_SECRET = $POD_ENV_VAR_FROM_SECRET or `echo $POD_ENV_VAR_FROM_SECRET | tr [a-z] [A-Z]`
        echo "OUTSIDE_EMPTY_POD_ENV_VAR_FROM_SECRET = '$EMPTY_POD_ENV_VAR_FROM_SECRET'"
        echo OUTSIDE_JAVA_HOME_X = $JAVA_HOME_X
        echo OUTSIDE_GLOBAL = $GLOBAL
        '''
        stage('Run busybox') {
            container('busybox') {
                sh 'echo inside container'
                sh '''set +x
                echo INSIDE_CONTAINER_BUILD_NUMBER = $BUILD_NUMBER
                echo INSIDE_CONTAINER_JOB_NAME = $JOB_NAME
                echo INSIDE_CONTAINER_ENV_VAR = $CONTAINER_ENV_VAR
                echo INSIDE_CONTAINER_ENV_VAR_LEGACY = $CONTAINER_ENV_VAR_LEGACY
                echo INSIDE_CONTAINER_ENV_VAR_FROM_SECRET = $CONTAINER_ENV_VAR_FROM_SECRET or `echo $CONTAINER_ENV_VAR_FROM_SECRET | tr [a-z] [A-Z]`
                echo INSIDE_POD_ENV_VAR = $POD_ENV_VAR
                echo INSIDE_POD_ENV_VAR_FROM_SECRET = $POD_ENV_VAR_FROM_SECRET or `echo $POD_ENV_VAR_FROM_SECRET | tr [a-z] [A-Z]`
                echo "INSIDE_EMPTY_POD_ENV_VAR_FROM_SECRET = '$EMPTY_POD_ENV_VAR_FROM_SECRET'"
                echo INSIDE_JAVA_HOME_X = $JAVA_HOME_X
                echo INSIDE_JAVA_HOME = $JAVA_HOME
                echo INSIDE_GLOBAL = $GLOBAL
                '''
            }
            container('jnlp') {
                sh '''set +x
                echo JNLP_JAVA_HOME = $JAVA_HOME
                echo JNLP_JAVA_HOME_X = $JAVA_HOME_X
                '''
            }
            container('java17') {
                sh '''set +x
                echo JAVA17_HOME = $JAVA_HOME
                echo JAVA17_HOME_X = $JAVA_HOME_X
                '''
            }
            container('java21') {
                sh '''set +x
                echo JAVA21_HOME = $JAVA_HOME
                echo JAVA21_HOME_X = $JAVA_HOME_X
                '''
            }
        }
    }
}
