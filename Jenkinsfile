node('docker') {
    checkout scm
    withEnv(["WSTMP=${pwd tmp: true}"]) {
        try {
            sh 'bash kind.sh'
        } finally {
            if (fileExists("$WSTMP/kindlogs/docker-info.txt")) {
                archiveArtifacts "$WSTMP/kindlogs"
            }
        }
    }
}
