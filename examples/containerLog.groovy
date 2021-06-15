podTemplate(yaml: '''
              apiVersion: v1
              kind: Pod
              metadata:
                labels:
                  some-label: some-label-value
              spec:
                containers:
                - name: maven
                  image: maven:3.8.1-jdk-8
                  command:
                  - sleep
                  args:
                  - 99d
                  tty: true
                - name: mongo
                  image: mongo
''') {
  node(POD_LABEL) {
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
