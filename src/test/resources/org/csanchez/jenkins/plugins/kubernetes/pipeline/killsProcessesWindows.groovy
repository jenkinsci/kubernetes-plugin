pipeline {
    agent {
        kubernetes {
            customWorkspace 'c:/s'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: "jenkins/inbound-agent:windowsservercore-1809"
    imagePullPolicy: IfNotPresent
    volumeMounts:
    - mountPath: /s
      name: s-volume
    - mountPath: /s@tmp
      name: stmp-volume
    resources:
      cpu:
        request: 1
      memory:
        request: 2Gi
  volumes:
  - emptyDir: {}
    name: s-volume
  - emptyDir: {}
    name: stmp-volume
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
                        catchError(buildResult: 'SUCCESS', message: 'Failed to perform flaky task') {
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
