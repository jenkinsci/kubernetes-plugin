//noinspection GrPackage
podTemplate(label: '$NAME') {
    node ('$NAME') {
        stage('container log') {
            containerLog 'jnlp'
        }
    }
}
