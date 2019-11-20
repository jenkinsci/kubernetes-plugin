pipeline {
    agent {
        kubernetes {
            label "somelabel-${UUID.randomUUID().toString()}"
            defaultContainer "jnlpdocker"
            yaml """
         apiVersion: v1
         kind: Pod
         spec:
            containers:
            - name: container1
              image: nonexistent-docker-image
              command:
              - cat
              tty: true
        """
        }
    }
    stages {
        stage('Run') {
            steps {
                container('container1') {
                    sh """
                    will never run
                  """
                }
            }
        }
    }
}