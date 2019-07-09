def label = "maven-${UUID.randomUUID().toString()}" // TODO use POD_LABEL when available
def mvnOpts = '-DskipTests -ntp -Darguments="-DskipTests -ntp"'

podTemplate(label: label, containers: [
  containerTemplate(name: 'maven', image: 'maven:3.6.1-jdk-8', ttyEnabled: true, command: 'cat')
  ], volumes: [
  persistentVolumeClaim(mountPath: '/root/.m2/repository', claimName: 'maven-repo', readOnly: false)
  ]) {

  node(label) {
    stage('Checkout') {
      checkout scm
    }
    stage('Release prepare') {
      container('maven') {
          sh "mvn -B ${mvnOpts} release:clean release:prepare -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion}"
      }
    }
    stage('Release perform') {
      container('maven') {
          sh "mvn -B ${mvnOpts} release:perform"
      }
    }
  }
}
