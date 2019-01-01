//noinspection GrPackage
podTemplate(label: 'getContainerLog') {
    node ('getContainerLog') {
        stage('container log') {
            containerLog 'jnlp'
        }
    }
}
