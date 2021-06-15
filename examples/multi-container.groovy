/**
 * This pipeline describes a multi container job, running Maven and Golang builds
 */

podTemplate(yaml: '''
              apiVersion: v1
              kind: Pod
              spec:
                containers:
                - name: maven
                  image: maven:3.8.1-jdk-8
                  command:
                  - sleep
                  args:
                  - 99d
                - name: golang
                  image: golang:1.16.5
                  command:
                  - sleep
                  args:
                  - 99d
'''
  ) {

  node(POD_LABEL) {
    stage('Build a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      container('maven') {
        sh 'mvn -B -ntp clean package -DskipTests'
      }
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
