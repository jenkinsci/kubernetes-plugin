podTemplate(label: 'mypod', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {

    node ('mypod') {
      stage('Run') {
        container('busybox') {
          sh """
            echo "PID file: \$(find ../.. -iname pid))"
            echo "PID file contents: \$(find ../.. -iname pid -exec cat {} \\;)"
            test -n "\$(cat \$(find ../.. -iname pid))"
          """
        }
      }
    }
}