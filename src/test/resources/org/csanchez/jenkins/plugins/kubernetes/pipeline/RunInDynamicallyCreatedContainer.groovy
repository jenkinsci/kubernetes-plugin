timeout ([time: 10, unit: 'MINUTES']) {
    def label = "jenkins-slave-${UUID.randomUUID().toString()}"
    podTemplate(
        label: label,
        yaml: '''
spec:
  containers:
    - name: jnlp
''',
        containers: [
            containerTemplate(name: 'jnlp',
                image: 'jenkins/jnlp-slave:latest',
                alwaysPullImage: true,
                args: '${computer.jnlpmac} ${computer.name}',
            ),
            containerTemplate(
                name: 'docker-dind',
                image: 'docker:19-dind',
                alwaysPullImage: true,
                privileged: true,
                envVars: [
                    envVar(key: 'DOCKER_TLS_CERTDIR', value: '')
                ],
            ),
            containerTemplate(
                name: 'docker',
                image: 'docker:19',
                alwaysPullImage: true,
                ttyEnabled: true,
                command: 'cat',
                envVars: [
                    envVar(key: 'DOCKER_HOST', value: 'tcp://localhost:2375')
                ],
            ),
        ]
    ) {
        node(label) {
            stage('build') {
                container('docker') {
                    sh 'echo "FROM ubuntu:bionic" > Dockerfile'

                    def tag = "test:${env.BUILD_ID}".toLowerCase()
                    devTools = docker.build(tag, "--pull -f Dockerfile .")

                    devTools.inside() {
                        sh 'whoami'
                    }
                }
            }
        }
    }
}
