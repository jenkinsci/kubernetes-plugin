pipeline {
    agent {
        kubernetes {
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: "jenkins/inbound-agent:windowsservercore-1809"
    imagePullPolicy: IfNotPresent
    resources:
      cpu:
        request: 1
      memory:
        request: 2Gi
  nodeSelector:
    kubernetes.io/os: windows
"""
        }
    }
    stages {
        stage('Timeout'){
            options {
                timeout(time: 5, unit: "SECONDS")
            }
            steps{
                container('jnlp') {
                    script {
                        // Before PR#1724 this would fail as windows processes were not killed
                        // and hence files locked. The test is a bit unrealistic as I want it
                        // to be fast and deterministic, but imagine that instead of the ping we execute
                        // a big checkout that locks some files and prevents next steps to execute
                        catchError {
                            bat 'ping 127.0.0.1 -n 3601 > test.txt' // Imagine this is a git checkout
                        }
                        bat 'rename test.txt test2.txt'
                        bat 'echo "It worked!"'
                    }
                }
            }
        }
    }
}
