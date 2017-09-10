//noinspection GrPackage
podTemplate(label: 'mypod') {
    node ('mypod') {
        stage('container log') {
            containerLog 'jnlp'
        }
    }
}
