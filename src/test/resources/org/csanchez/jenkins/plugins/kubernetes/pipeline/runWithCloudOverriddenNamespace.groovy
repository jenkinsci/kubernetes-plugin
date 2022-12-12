podTemplate(volumes: [emptyDirVolume(mountPath: '/my-mount')]) {

    node(POD_LABEL) {
        semaphore 'pod'
        container(name: 'jnlp') {
            sh 'cat /var/run/secrets/kubernetes.io/serviceaccount/namespace'
        }
    }
}
