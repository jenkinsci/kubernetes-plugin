podTemplate(yaml: '''
spec:
  volumes:
  - name: jenkins-agent
    projected:
      sources:
      - secret:
          name: container-secret
          items:
            - key: password
              path: my-group/my-password
              mode: 0777
''') {
  node(POD_LABEL){
    sh 'true'
  }
}
