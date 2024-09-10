/**
 * This pipeline will execute a simple maven build, using a Persistent Volume Claim to store the local Maven repository
 *
 * A PersistentVolumeClaim needs to be created ahead of time with the definition in maven-with-cache-pvc.yml
 *
 * NOTE that typically writable volumes can only be attached to one Pod at a time, so you can't execute
 * two concurrent jobs with this pipeline. Or change readOnly: true after the first run
 */

podTemplate(agentContainer: 'maven', agentInjection: true, containers: [
  containerTemplate(name: 'maven', image: 'maven:3.9.9-eclipse-temurin-17')
  ], volumes: [genericEphemeralVolume(accessModes: 'ReadWriteOnce', mountPath: '/root/.m2/repository', requestsSize: '1Gi')]) {

  node(POD_LABEL) {
    stage('Build a Maven project') {
      git 'https://github.com/jenkinsci/kubernetes-plugin.git'
      sh 'mvn -B -ntp clean package -DskipTests'
    }
  }
}
