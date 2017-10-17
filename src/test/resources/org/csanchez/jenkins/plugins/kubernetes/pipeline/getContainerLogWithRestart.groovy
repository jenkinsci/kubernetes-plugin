//noinspection GrPackage
podTemplate(label: 'mypod', namespace: 'default')
{
    node ('mypod') {
        stage('container log') {
            semaphore 'wait'
            containerLog 'jnlp'
        }
    }
}
