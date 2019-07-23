// Step namespace should have priority over anything else.
podTemplate(
    namespace: '$OVERRIDDEN_NAMESPACE',
    volumes: [emptyDirVolume(mountPath: '/my-mount')], 
    containers: [
      containerTemplate(name: 'jnlp', image: 'jenkins/jnlp-slave:3.10-1-alpine', args: '${computer.jnlpmac} ${computer.name}')
    ]) {

    node(POD_LABEL) {
        container(name: 'jnlp') {
            // Need a newline cf. https://github.com/jenkinsci/workflow-durable-task-step-plugin/pull/103
            sh "cat /var/run/secrets/kubernetes.io/serviceaccount/namespace && echo"
        }
    }
}
