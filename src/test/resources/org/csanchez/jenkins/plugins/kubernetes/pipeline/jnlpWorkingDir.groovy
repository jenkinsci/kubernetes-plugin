podTemplate(containers: [
    containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:3283.v92c105e0f819-7', workingDir: '/home/jenkins'),
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
