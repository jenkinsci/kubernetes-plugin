podTemplate(label: '$NAME', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    semaphore 'podTemplate'
    node ('$NAME') {
      semaphore 'pod'
      stage('Run') {
        container('busybox') {
          echo 'container=' + POD_CONTAINER
          sh """
            ## durable-task plugin generates a script.sh file.
            ##
            echo "script file: \$(find ../../.. -iname script.sh))"
            echo "script file contents: \$(find ../../.. -iname script.sh -exec cat {} \\;)"
            test -n "\$(cat \"\$(find ../../.. -iname script.sh)\")"
          """
        }
      }
    }
}
semaphore 'after-podtemplate'
