node {
  stage('Run') {
    withKubeConfig([credentialsId: 'cred1234', caCertificate: 'a-certificate', serverUrl: 'https://localhost:6443']) {
      sh 'cat $KUBECONFIG > configDump'
    }
  }
}
