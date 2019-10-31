podTemplate(containers: [containerTemplate(name: 'shell', image: 'ubuntu', command: 'sleep', args: '99d')]) {
    node(POD_LABEL) {
        container('shell') {
            // Note that trap apparently does not work in Ubuntu [da]sh or Busybox [a]sh.
            sh(/set +x; bash -c 'trap "echo shut down gracefully" EXIT; echo starting to sleep; sleep 9999999'/)
        }
    }
}
