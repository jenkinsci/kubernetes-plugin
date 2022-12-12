properties([
    disableConcurrentBuilds(abortPrevious: true),
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    buildDiscarder(logRotator(numToKeepStr: '5')),
])
parallel kind: {
    node('docker') {
        timeout(90) {
            checkout scm
            withEnv(["WSTMP=${pwd tmp: true}"]) {
                try {
                    sh 'bash kind.sh'
                    dir (WSTMP) {
                        junit 'surefire-reports/*.xml'
                    }
                } finally {
                    dir (WSTMP) {
                        if (fileExists('kindlogs/docker-info.txt')) {
                            archiveArtifacts 'kindlogs/'
                        }
                    }
                }
            }
        }
    }
}, jdk17: {
    node('maven-17') {
        timeout(60) {
            checkout scm
            sh 'mvn -B -ntp -Dset.changelist -Dmaven.test.failure.ignore clean install'
            infra.prepareToPublishIncrementals()
            junit 'target/surefire-reports/*.xml'
        }
    }
}, failFast: true
infra.maybePublishIncrementals()
