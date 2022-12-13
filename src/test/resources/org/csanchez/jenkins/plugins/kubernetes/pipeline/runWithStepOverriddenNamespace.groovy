// Step namespace should have priority over anything else.
podTemplate(
    namespace: '$OVERRIDDEN_NAMESPACE',
    volumes: [emptyDirVolume(mountPath: '/my-mount')]) {

    node(POD_LABEL) {
        semaphore 'pod'
        container(name: 'jnlp') {
            sh 'cat /var/run/secrets/kubernetes.io/serviceaccount/namespace'
        }
    }
}
