podTemplate(volumes: [emptyDirVolume(mountPath: '/my-mount')], containers: [
        containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:4.3-4-alpine', args: '${computer.jnlpmac} ${computer.name}')
]) {

    node(POD_LABEL) {
        container(name: 'jnlp') {
            sh 'cat /var/run/secrets/kubernetes.io/serviceaccount/namespace'
        }
    }
}
