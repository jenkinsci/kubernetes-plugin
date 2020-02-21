podTemplate(containers: [
    containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat', workingDir: '/src')
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
