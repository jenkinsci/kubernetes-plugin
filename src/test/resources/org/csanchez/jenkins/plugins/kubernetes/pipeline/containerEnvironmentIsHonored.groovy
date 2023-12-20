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
    echo "from Groovy: ${env.PATH}"
    sh 'echo "outside container: $PATH"'
    container('alpine') {
      sh 'echo "inside container: $PATH"'
      withMaven(publisherStrategy: 'EXPLICIT', traceability: false) {
          sh 'echo "inside withMaven in container: $PATH"'
      }
    }
  }
}
