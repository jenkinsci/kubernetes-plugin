podTemplate(label: '$NAME-parent', containers: [
		containerTemplate(name: 'golang', image: 'golang:1.6.3-alpine', ttyEnabled: true, command: '/bin/cat'),
	]) {

	podTemplate(containers: [
		containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: '/bin/cat'),
	]) {

		node(POD_LABEL) {
			stage('Nested') {
				container('maven') {
					sh "mvn -version"
				}
			}
			stage('Parent') {
				container('golang') {
					sh "go version"
				}
			}
		}
	}
}
