/**
 * OpenShift runs containers with a custom UID, overridding the UID defined in docker images.
 * This is an example of how to run docker images from DockerHub in OpenShift. It comes with no warranty.
 *
 * Define HOME for containers running in OpenShift
 * Define user.home system property for maven as it relies on /etc/passwd to infer its value
 */
podTemplate(yaml:'''
              spec:
                containers:
                - name: jnlp
                  image: jenkins/inbound-agent
                  volumeMounts:
                  - name: home-volume
                    mountPath: /home/jenkins
                  env:
                  - name: HOME
                    value: /home/jenkins
                - name: maven
                  image: maven:3.8.1-jdk-8
                  command:
                  - sleep
                  args: 
                  - 99d
                  volumeMounts:
                  - name: home-volume
                    mountPath: /home/jenkins
                  env:
                  - name: HOME
                    value: /home/jenkins
                  - name: MAVEN_OPTS
                    value: -Duser.home=/home/jenkins
                volumes:
                - name: home-volume
                  emptyDir: {}
''') {
  node(POD_LABEL) {
    stage('Build a Maven project') {
      container('maven') {
        git 'https://github.com/jenkinsci/kubernetes-plugin.git'
        sh 'mvn -B -ntp clean package -DskipTests'
      }
    }
  }
}
