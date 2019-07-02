podTemplate(label: '$NAME-1', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    semaphore 'podTemplate1'
    node ('mypod') {
        semaphore 'pod1'
        stage('Run') {
            container('busybox') {
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

podTemplate(label: '$NAME-2', containers: [
        containerTemplate(name: 'busybox2', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
]) {
    semaphore 'podTemplate2'
    node ('$NAME-2') {
        semaphore 'pod2'
        stage('Run') {
            container('busybox2') {

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
