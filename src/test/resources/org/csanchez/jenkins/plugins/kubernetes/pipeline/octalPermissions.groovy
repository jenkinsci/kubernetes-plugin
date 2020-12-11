podTemplate(yaml: '''
spec:
  volumes:
  - name: jenkins-agent
    configMap:
      name: jenkins-agent
      defaultMode: 0777
''') {
  node(POD_LABEL){
    sh 'true'
  }
}
