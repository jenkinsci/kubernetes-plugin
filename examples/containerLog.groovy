podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'maven', image: 'maven:alpine', ttyEnabled: true, command: 'cat'),
        containerTemplate(name: 'mongo', image: 'mongo'),
]) {
    node('mypod') {
        stage('Integration Test') {
            try {
                container('maven') {
                    sh 'nc -z localhost:27017 && echo "connected to mongo db"'
                    // sh 'mvn -B clean failsafe:integration-test' // real integration test

                    def mongoLog = containerLog(name: 'mongo', returnLog: true, tailingLines: 5, sinceSeconds: 20, limitBytes: 50000)
                    assert mongoLog.contains('connection accepted from 127.0.0.1:')
                    sh 'echo failing build; false'
                }
            } catch (Exception e) {
                containerLog 'mongo'
                throw e
            }
        }
    }
}
