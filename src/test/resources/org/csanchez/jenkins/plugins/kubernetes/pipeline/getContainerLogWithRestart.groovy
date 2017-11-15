//noinspection GrPackage
podTemplate(label: 'mypod')
{
    node ('mypod') {
        stage('container log') {
            semaphore 'wait'
            containerLog 'jnlp'
        }
    }
}
