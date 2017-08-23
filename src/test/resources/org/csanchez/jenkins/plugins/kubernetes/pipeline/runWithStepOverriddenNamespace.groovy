// Step namespace should have priority over anything else.
podTemplate(
    namespace: 'kubernetes-plugin-test-overridden-namespace2', label: 'mypod',
    volumes: [emptyDirVolume(mountPath: '/my-mount')], 
    containers: [
      containerTemplate(name: 'jnlp', image: 'jenkinsci/jnlp-slave:2.62-alpine', args: '${computer.jnlpmac} ${computer.name}')
    ]) {

    node ('mypod') {
        container(name: 'jnlp') {
            sh "cat /var/run/secrets/kubernetes.io/serviceaccount/namespace"
        }
    }
}
