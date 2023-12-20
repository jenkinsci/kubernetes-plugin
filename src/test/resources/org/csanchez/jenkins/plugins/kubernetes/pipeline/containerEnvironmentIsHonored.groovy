podTemplate(yaml: '''
spec:
  containers:
  - name: jnlp
    image: jenkins/inbound-agent:3192.v713e3b_039fb_e-1
    # PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
  - name: alpine
    image: alpine:3.19.0
    # PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
    command:
    - sleep
    args:
    - infinity
''') {
  node(POD_LABEL) {
    echo "from Groovy outside container: ${env.PATH}"
    sh 'echo "from shell outside container: $PATH"'
    withEnv(['PATH+foo=/bar']) {
      echo "from Groovy outside container with override: ${env.PATH}"
      sh 'echo "from shell outside container with override: $PATH"'
    }
    container('alpine') {
      echo "from Groovy inside container: ${env.PATH}"
      sh 'echo "from shell inside container: $PATH"'
      withEnv(['PATH+foo=/bar']) {
        echo "from Groovy inside container with override: ${env.PATH}"
        sh 'echo "from shell inside container with override: $PATH"'
      }
    }
  }
}
