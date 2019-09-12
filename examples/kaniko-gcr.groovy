/**
 * This pipeline will build and deploy a Docker image with Kaniko
 * https://github.com/GoogleContainerTools/kaniko
 * without needing a Docker host
 *
 * You need to create a secret with your registry credentials as described in
 * https://github.com/GoogleContainerTools/kaniko#kubernetes-secret
 * kubectl create secret generic kaniko-secret --from-file=kaniko-secret.json
 */

podTemplate(yaml: """
kind: Pod
metadata:
  name: kaniko
spec:
  containers:
  - name: kaniko
    image: gcr.io/kaniko-project/executor:debug
    imagePullPolicy: Always
    command:
    - /busybox/cat
    tty: true
    volumeMounts:
      - name: kaniko-secret
        mountPath: /secret
    env:
      - name: GOOGLE_APPLICATION_CREDENTIALS
        value: /secret/kaniko-secret.json
  volumes:
    - name: kaniko-secret
      secret:
        secretName: kaniko-secret
"""
  ) {

  node(POD_LABEL) {
    stage('Build with Kaniko') {
      git 'https://github.com/jenkinsci/docker-jnlp-slave.git'
      container('kaniko') {
        withEnv(['PATH+EXTRA=/busybox:/kaniko']) {
          sh '/kaniko/executor -c `pwd` --cache=true --destination=gcr.io/myprojectid/myimage'
        }
      }
    }
  }
}
