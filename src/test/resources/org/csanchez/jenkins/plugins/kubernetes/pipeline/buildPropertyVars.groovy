pipeline {
	agent {

        kubernetes{
                label 'openjdk'
                containerTemplate{
                        name 'openjdk'
                        image 'openjdk'
                        workingDir '/home/jenkins'
                        ttyEnabled true
                        command 'cat'
                        args ''
                }
        }
}


    stages{
        stage('Build') {
            steps {

                sh 'echo DEFAULT_BUILD_NUMBER: ${BUILD_NUMBER}'
                container('jnlp')
                {
                    sh 'echo JNLP_BUILD_NUMBER: ${BUILD_NUMBER}'
                }
                container('openjdk'){
                    sh 'echo OPENJDK_BUILD_NUMBER: ${BUILD_NUMBER}'
                }
            }
        }
    }
}