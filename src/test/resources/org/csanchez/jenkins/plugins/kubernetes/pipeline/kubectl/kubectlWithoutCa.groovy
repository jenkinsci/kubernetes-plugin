node {
  stage('Run') {
    withKubeConfig([credentialsId: 'cred1234', serverUrl: 'https://localhost:6443']) {
      sh 'cat $KUBECONFIG > configDump'
    }
  }
}
