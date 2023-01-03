timeout ([time: 10, unit: 'MINUTES']) {
    podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  volumes:
  - name: docker-socket
    emptyDir: {}
  containers:
  - name: docker
    image: docker:20.10.21
    readinessProbe:
      exec:
        command:
        - sh
        - -c
        - ls -S /var/run/docker.sock
    command:
    - sleep
    args:
    - 99d
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
  - name: docker-daemon
    image: docker:20.10.21-dind
    securityContext:
      privileged: true
    volumeMounts:
    - name: docker-socket
      mountPath: /var/run
''') {
        node(POD_LABEL) {
            stage('build') {
                container('docker') {
                    sh 'echo "FROM ubuntu:bionic" > Dockerfile'

                    def tag = "test:${env.BUILD_ID}".toLowerCase()
                    devTools = docker.build(tag, "--pull -f Dockerfile .")

                    devTools.inside() {
                        sh 'whoami'
                    }
                }
            }
        }
    }
}
