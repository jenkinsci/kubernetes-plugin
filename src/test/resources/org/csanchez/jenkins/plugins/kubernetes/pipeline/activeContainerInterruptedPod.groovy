podTemplate(containers: [containerTemplate(name: 'shell', image: 'ubuntu', command: 'sleep', args: 'infinity')]) {
    node(POD_LABEL) {
        container('shell') {
            sh '''
              set +x
              bash -c 'trap "echo shut down gracefully" EXIT; echo starting to sleep; sleep 9999999'
            '''
        }
    }
}
