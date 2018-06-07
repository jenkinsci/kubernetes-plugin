def label = "maven-${UUID.randomUUID().toString()}"
def mvnOpts = '-DskipTests -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -Darguments="-DskipTests -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn"'

podTemplate(label: label, containers: [
  containerTemplate(name: 'maven', image: 'maven:alpine', ttyEnabled: true, command: 'cat')
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
          sh """
          apk update && apk add git
          mvn -B ${mvnOpts} release:perform
          """
      }
    }
  }
}
