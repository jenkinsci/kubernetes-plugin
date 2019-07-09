/**
 * This pipeline will run a Docker image build
 */

podTemplate(yaml: """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: docker
    image: docker:1.11
    command: ['cat']
    tty: true
    volumeMounts:
    - name: dockersock
      mountPath: /var/run/docker.sock
  volumes:
  - name: dockersock
    hostPath:
      path: /var/run/docker.sock
"""
  ) {

  def image = "jenkins/jnlp-slave"
  node(POD_LABEL) {
    stage('Build Docker image') {
      git 'https://github.com/jenkinsci/docker-jnlp-slave.git'
      container('docker') {
        sh "docker build -t ${image} ."
      }
    }
  }
}
