//noinspection GrPackage
podTemplate {
    node(POD_LABEL) {
        stage('container log') {
            containerLog 'jnlp'
        }
    }
}
