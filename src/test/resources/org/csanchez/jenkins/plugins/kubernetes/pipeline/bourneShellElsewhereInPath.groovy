podTemplate(containers: [containerTemplate(name: 'kaniko', image: 'gcr.io/kaniko-project/executor:debug', command: 'sleep', args: '99d')]) {
    node(POD_LABEL) {
        container(name: 'kaniko') {
            sh 'echo $PATH'
        }
    }
}
