def podWithHostPort(String label, Integer port, Closure body) {
    podTemplate(
        name: label,
        label: label,
        serviceAccount: 'jenkins',
        containers: [
            containerTemplate(
                name: 'hyperkube',
                image: 'gcr.io/google_containers/hyperkube-amd64:v1.9.7',
                command: '/bin/cat',
                ports: [
                    portMapping(
                        name: label.toLowerCase(),
                        containerPort: port,
                        hostPort: port
                    )
                ],
                ttyEnabled: true,
            )
        ]
    ) {
        node(label) {
            stage(label) {
                container('hyperkube') {
                    body()
                }
            }
        }
    }
}

parallel(
    'hostPort1': {
        podWithHostPort('hostPort1', 10001) {
            sh("/hyperkube kubectl -n kubernetes-plugin-test get pod \${HOSTNAME} -o yaml")
        }
    },
    'hostPort2': {
        podWithHostPort('hostPort2', 10002) {
            sh("/hyperkube kubectl -n kubernetes-plugin-test get pod \${HOSTNAME} -o yaml")
        }
    }
)
