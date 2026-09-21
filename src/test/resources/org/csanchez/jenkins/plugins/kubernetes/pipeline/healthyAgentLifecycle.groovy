podTemplate(yaml: '') {
    node(POD_LABEL) {
        sh 'true'
    }
}
