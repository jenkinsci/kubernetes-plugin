//noinspection GrPackage
podTemplate(cloud: 'kubernetes-plugin-test', label: 'mypod') {
    node ('mypod') {
        stage('container log') {
            containerLog 'jnlp'
        }
    }
}
