podTemplate(volumes: [dynamicPVC(requestsSize: '10Gi', mountPath: '/tmp/mountPath')], containers: [
        containerTemplate(name: 'jnlp', image: 'jenkins/inbound-agent:4.11-1-jdk8', args: '${computer.jnlpmac} ${computer.name}')
], yaml:'''
spec:
  securityContext:
    fsGroup: 1000
''') {

    node(POD_LABEL) {
        container(name: 'jnlp') {
            sh 'cat /var/run/secrets/kubernetes.io/serviceaccount/namespace'
            git 'https://github.com/jenkinsci/kubernetes-plugin.git'
        }
    }
}
