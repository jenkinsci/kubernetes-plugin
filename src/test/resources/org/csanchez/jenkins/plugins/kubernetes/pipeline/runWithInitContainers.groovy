podTemplate(label: 'mypod',
    initContainers: [
            containerTemplate(name: 'busybox', image: 'alpine', command: 'touch /home/jenkins/key')
    ],
    containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat')
    ]) {

    node ('mypod') {
        stage('Run busybox') {
            container('busybox') {
                sh 'cat key'
            }
        }
    }
}
