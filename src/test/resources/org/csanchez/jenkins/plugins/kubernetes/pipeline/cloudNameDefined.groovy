podTemplate(cloudName: 'kubernetes',
        containers: [
        containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: 'cat')
]) {
    node(POD_LABEL) {
        stage('container log') {
            container('maven') {
                sh 'mvn -version'
            }
        }
    }
}
