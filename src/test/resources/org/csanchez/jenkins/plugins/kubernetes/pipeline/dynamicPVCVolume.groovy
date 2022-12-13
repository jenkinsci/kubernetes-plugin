podTemplate(volumes: [dynamicPVC(requestsSize: '10Gi', mountPath: '/tmp/mountPath')], yaml:'''
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
