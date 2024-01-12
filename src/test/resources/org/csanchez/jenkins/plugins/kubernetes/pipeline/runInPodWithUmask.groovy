podTemplate(containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node(POD_LABEL) {
      stage('Run') {
        container(name:'busybox', umask: '002') {
          sh """
            echo "Umask: \$(umask)"
          """
        }
      }
    }
}