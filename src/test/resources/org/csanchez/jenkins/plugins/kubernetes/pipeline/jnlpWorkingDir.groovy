podTemplate(containers: [
    containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:3077.vd69cf116da_6f-3-jdk11', workingDir: '/home/jenkins'),
    containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat')
]) {
    node (POD_LABEL) {
        stage('Run') {
            sh 'env | sort'
            container('busybox') {
                sh 'env | sort'
            }
        }
    }
}
