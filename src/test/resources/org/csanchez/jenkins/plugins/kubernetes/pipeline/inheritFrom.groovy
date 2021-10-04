podTemplate(inheritFrom: 'standard') {
  node(POD_LABEL) {
    sh 'true'
  }
}
