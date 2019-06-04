podTemplate(label: 'bourneShellElsewhereInPath', containers: [containerTemplate(name: 'kaniko', image: 'gcr.io/kaniko-project/executor:debug', command: 'sleep', args: '99d')]) {
    node('bourneShellElsewhereInPath') {
        container(name: 'kaniko') {
            sh 'ls -la /'
        }
    }
}
