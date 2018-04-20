/**
 * This pipeline will build and deploy a Docker image with Kaniko
 * https://github.com/GoogleContainerTools/kaniko
 * without needing a Docker host
 *
 * You need to create a jenkins-docker-cfg secret with your docker config
 * as described in
 * https://kubernetes.io/docs/tasks/configure-pod-container/pull-image-private-registry/#create-a-secret-in-the-cluster-that-holds-your-authorization-token
 */

 def label = "kaniko-${UUID.randomUUID().toString()}"

 podTemplate(name: 'kaniko', label: label, yaml: """
 kind: Pod
 metadata:
   name: kaniko
 spec:
   containers:
   - name: kaniko
     image: csanchez/kaniko:jenkins # we need a patched version of kaniko for now
     imagePullPolicy: Always
     command:
     - cat
     tty: true
     volumeMounts:
       - name: jenkins-docker-cfg
         mountPath: /root/.docker
   volumes:
     - name: jenkins-docker-cfg
       secret:
         secretName: jenkins-docker-cfg
 """
   ) {

   node(label) {
     stage('Build with Kaniko') {
       git 'https://github.com/jenkinsci/docker-jnlp-slave.git'
       container('kaniko') {
           sh '/kaniko/executor -c . --insecure-skip-tls-verify --destination=mydockerregistry:5000/myorg/myimage'
       }
     }
   }
 }
