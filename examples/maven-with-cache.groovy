/**
 * This pipeline will execute a simple maven build, using a Persistent Volume Claim to store the local Maven repository
 *
 * A PersistentVolumeClaim needs to be created ahead of time with the definition in maven-with-cache-pvc.yml
 *
 * NOTE that typically writable volumes can only be attached to one Pod at a time, so you can't execute
 * two concurrent jobs with this pipeline. Or change readOnly: true after the first run
 */

podTemplate(containers: [
  containerTemplate(name: 'maven', image: 'maven:3.8.1-jdk-8', command: 'sleep', args: '99d')
  ], volumes: [
  persistentVolumeClaim(mountPath: '/root/.m2/repository', claimName: 'maven-repo', readOnly: false)
  ]) {

  node(POD_LABEL) {
    stage('Build a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      container('maven') {
        sh 'mvn -B -ntp clean package -DskipTests'
      }
    }
  }
}
