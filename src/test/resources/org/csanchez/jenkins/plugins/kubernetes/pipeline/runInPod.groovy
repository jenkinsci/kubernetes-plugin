podTemplate(label: '$NAME', containers: [
        containerTemplate(name: 'busybox', image: 'busybox', ttyEnabled: true, command: '/bin/cat'),
    ]) {
    semaphore 'podTemplate'
    node ('$NAME') {
      semaphore 'pod'
      stage('Run') {
        container('busybox') {
          echo "container=$POD_CONTAINER"
          // durable-task plugin generates a script.sh file
          sh '''
            echo "script file: $(find ../../.. -path '*@tmp/durable-*/script.sh'))"
            echo "script file contents: $(find ../../.. -path '*@tmp/durable-*/script.sh' -exec cat {} ';')"
            test -n "$(cat "$(find ../../.. -path '*@tmp/durable-*/script.sh')")"
          '''
        }
      }
    }
}
semaphore 'after-podtemplate'
