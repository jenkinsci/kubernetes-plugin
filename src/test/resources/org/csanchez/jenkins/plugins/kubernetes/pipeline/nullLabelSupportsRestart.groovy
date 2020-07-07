node {
  stage('Run') {
    container('busybox') {
      sh 'for i in `seq 1 10`; do echo $i; sleep 5; done'
    }
    echo 'finished the test!'
  }
}
