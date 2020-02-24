podTemplate {
    node(POD_LABEL) {
        sh 'echo OK running'
    }
}
