podTemplate(label: 'mypod', volumes: [emptyDirVolume(mountPath: '/my-mount')], containers: [
        containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:3.10-1-alpine', args: '${computer.jnlpmac} ${computer.name}'),
        containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'golang', image: 'golang:1.6.3-alpine', ttyEnabled: true, command: 'cat')
]) {

    node ('mypod') {
        sh "echo My Kubernetes Pipeline"
        sh "ls /"

        stage('Run maven') {
            container('maven') {
                sh 'mvn -version'
            }
        }


    }
}
