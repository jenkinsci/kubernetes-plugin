node('docker') {
    withEnv(["WSTMP=${pwd tmp: true}"]) {
        sh 'bash kind.sh'
        archiveArtifacts "$WSTMP/kindlogs"
    }
}
