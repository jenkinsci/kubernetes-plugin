podTemplate(containers: [
    containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:0a4c2cf1212134f2179911ca9d00ef70a5435b16e361cd7e9a1c19489492bfdc', workingDir: '/home/jenkins'),
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
