podTemplate(yaml: '''
spec:
  containers:
  - name: curl
    image: curlimages/curl
    command:
    - sleep
    args:
    - infinity
    securityContext:
      runAsUser: 1000
  - name: service
    image: us-docker.pkg.dev/google-samples/containers/gke/hello-app:1.0
    ports:
    - containerPort: 8080
''') {
  node(POD_LABEL) {
    container('curl') {
      sh 'curl -si localhost:8080'
    }
  }
}
