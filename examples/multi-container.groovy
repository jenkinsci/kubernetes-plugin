/**
 * This pipeline describes a multi container job, running Maven and Golang builds
 */

podTemplate(agentContainer: 'maven',
            agentInjection: true,
            yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.9.9-eclipse-temurin-17
  - name: golang
    image: golang:1.23.1-bookworm
    command:
    - sleep
    args:
    - 99d
'''
  ) {
  node(POD_LABEL) {
    stage('Build a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      sh 'mvn -B -ntp clean package -DskipTests'
    }
    stage('Build a Golang project') {
      git url: 'https://github.com/hashicorp/terraform.git', branch: 'main'
      container('golang') {
        sh '''
          mkdir -p /go/src/github.com/hashicorp
          ln -s `pwd` /go/src/github.com/hashicorp/terraform
          cd /go/src/github.com/hashicorp/terraform && make
        '''
      }
    }
  }
}
