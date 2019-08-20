podTemplate(containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node(POD_LABEL) {
      stage('Run') {
        container(name:'busybox', shell: '/bin/bash') {
          sh """
            echo "Run BusyBox shell"
          """
        }
      }
    }
}