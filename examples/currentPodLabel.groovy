podTemplate(
        containers: [
                containerTemplate(name: 'mvn', image: 'maven', ttyEnabled: true, command: 'cat'),
        ],
) {
    node(current_pod_label) {
        container('mvn') {
            sh 'mvn --version'
        }
    }
}

