podTemplate(label: '$NAME-parent', containers: [
		containerTemplate(name: 'golang', image: 'golang:1.6.3-alpine', ttyEnabled: true, command: '/bin/cat'),
	]) {

	podTemplate(label: '$NAME', inheritFrom: '',  containers: [
		containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: '/bin/cat'),
	]) {

		node ('$NAME') {
			stage('Nested') {
				container('maven') {
					sh "mvn -version"
				}
			}
			stage('Parent') {
				container('golang') {
                    script {
                        try {
                            sh "go version"
                            error("Should not inherit")
                        } catch (e) {
                            // ignored
                        }
                    }
				}
			}
		}
	}
}
