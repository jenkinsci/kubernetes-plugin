podTemplate(cloud: 'kubernetes-plugin-test', label: 'mypod',
    envVars: [
        podEnvVar(key: 'POD_ENV_VAR', value: 'pod-env-var-value'),
    ],
	containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat',
		    envVars: [
		        containerEnvVar(key: 'CONTAINER_ENV_VAR', value: 'container-env-var-value'),
		    ],
        ),
    ]) {

    node ('mypod') {
        sh """
        echo OUTSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
        echo OUTSIDE_POD_ENV_VAR = \$POD_ENV_VAR
        """
	    stage('Run busybox') {
	        container('busybox') {
	            sh 'echo inside container'
	            sh """
	            echo INSIDE_CONTAINER_ENV_VAR = \$CONTAINER_ENV_VAR
	            echo INSIDE_POD_ENV_VAR = \$POD_ENV_VAR
	            """
	        }
	    }
    }
}
