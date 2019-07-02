podTemplate(label: '$NAME', containers: [containerTemplate(name: 'kaniko', image: 'gcr.io/kaniko-project/executor:debug', command: 'sleep', args: '99d')]) {
    node('$NAME') {
        container(name: 'kaniko') {
            sh 'echo $PATH'
        }
    }
}
