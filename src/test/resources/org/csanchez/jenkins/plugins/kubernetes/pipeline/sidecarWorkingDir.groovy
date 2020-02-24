podTemplate(containers: [
    containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat', workingDir: '/src')
]) {
    node (POD_LABEL) {
        stage('Run') {
            sh '[ "$(pwd)" = "/home/jenkins/agent/workspace/$JOB_NAME" ]'
            container('busybox') {
                sh '[ "$(pwd)" = "/src/workspace/$JOB_NAME" ]'
            }
        }
    }
}
