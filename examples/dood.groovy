/*
“Docker-outside-of-Docker”: runs a Docker-based build by connecting a Docker client inside the pod to the host daemon.
*/
podTemplate(yaml: '''
              apiVersion: v1
              kind: Pod
              spec:
                containers:
                - name: docker
                  image: docker:19.03.1
                  command:
                  - sleep
                  args:
                  - 99d
                  volumeMounts:
                  - name: dockersock
                    mountPath: /var/run/docker.sock
                volumes:
                - name: dockersock
                  hostPath:
                    path: /var/run/docker.sock
''') {
  node(POD_LABEL) {
    stage('Build Docker image') {
      git 'https://github.com/jenkinsci/docker-inbound-agent.git'
      container('docker') {
        sh 'docker build -t jenkins/inbound-agent .'
      }
    }
  }
}
