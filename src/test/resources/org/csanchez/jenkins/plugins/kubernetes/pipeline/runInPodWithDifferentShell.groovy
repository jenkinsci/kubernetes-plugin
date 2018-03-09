podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('mypod') {
      stage('Run') {
        container(name:'busybox', shell: '/bin/bash') {
          sh """
            echo "Run BusyBox shell"
          """
        }
      }
    }
}