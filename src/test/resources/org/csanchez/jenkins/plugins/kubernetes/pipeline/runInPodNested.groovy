podTemplate(label: 'mypod', containers: [
		containerTemplate(name: 'golang', image: 'golang:1.6.3-alpine', ttyEnabled: true, command: '/bin/cat'),
	]) {

	podTemplate(label: 'mypodNested', containers: [
		containerTemplate(name: 'maven', image: 'maven:3.3.9-jdk-8-alpine', ttyEnabled: true, command: '/bin/cat'),
	]) {

		node ('mypodNested') {
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
