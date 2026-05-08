podTemplate(yaml: '''
spec:
  containers:
  - name: ubuntu
    image: ubuntu
    command:
    - sleep
    args:
    - infinity
''') {
  def branches = [:]
  for (int i = 0; i < 10; i++) {
    branches["b$i"] = {
      while (true) {
        node(POD_LABEL) {
          container('ubuntu') {
            sh 'fgrep VERSION_ID /etc/os-release'
          }
        }
      }
    }
  }
  parallel branches
}
