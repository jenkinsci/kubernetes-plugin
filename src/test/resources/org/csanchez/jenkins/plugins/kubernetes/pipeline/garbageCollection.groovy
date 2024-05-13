podTemplate {
  node(POD_LABEL) {
    echo 'Running on remote agent'
    sh 'sleep 600'
  }
}
