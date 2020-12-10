/**
 * This pipeline describes a multi container job, running Maven and Golang builds
 */

podTemplate(yaml: """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.3.9-jdk-8-alpine
    command: ['cat']
    tty: true
  - name: golang
    image: golang:1.8.0
    command: ['sleep']
    args: ['9999999']
"""
  ) {

  node(POD_LABEL) {
    stage('Build a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      container('maven') {
        sh 'mvn -B clean package'
      }
    }

    stage('Build a Golang project') {
      git url: 'https://github.com/terraform-providers/terraform-provider-aws.git'
      container('golang') {
        sh """
        mkdir -p /go/src/github.com/terraform-providers
        ln -s `pwd` /go/src/github.com/terraform-providers/terraform-provider-aws
        cd /go/src/github.com/terraform-providers/terraform-provider-aws && make build
        """
      }
    }

  }
}
