podTemplate(yaml:'''
spec:
  activeDeadlineSeconds: 30
''') {
  node(POD_LABEL) {
    sh 'sleep 120'
  }
}
