podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  nodeSelector:
    disktype: special
''') {
    node(POD_LABEL) {
        sh 'true'
    }
}