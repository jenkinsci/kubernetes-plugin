properties([
    disableConcurrentBuilds(abortPrevious: true),
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    buildDiscarder(logRotator(numToKeepStr: '5')),
])

def splits
node('alpine') {
    checkout scm
    splits = splitTests parallelism: count(2), generateInclusions: true, estimateTestsFromFiles: true
}
def branches = [:]

for (int i = 1; i < splits.size() + 1; i++) {
    def num = i
    def split = splits[num]
    branches["kind-${num}"] = {
        node('docker') {
            timeout(90) {
                checkout scm
                try {
                    writeFile file: (split.includes ? "$WORKSPACE_TMP/includes.txt" : "$WORKSPACE_TMP/excludes.txt"), text: split.list.join("\n")
                    writeFile file: (split.includes ? "$WORKSPACE_TMP/excludes.txt" : "$WORKSPACE_TMP/includes.txt"), text: ''
                    sh 'kind.sh -Dsurefire.includesFile="$WORKSPACE_TMP/includes.txt -Dsurefire.excludesFile="$WORKSPACE_TMP/excludes.txt"'
                    dir (env.WORKSPACE_TMP) {
                        junit 'surefire-reports/*.xml'
                    }
                } finally {
                    dir (env.WORKSPACE_TMP) {
                        if (fileExists('kindlogs/docker-info.txt')) {
                            archiveArtifacts 'kindlogs/'
                        }
                    }
                }
            }
        }
    }
}
branches['jdk11'] = {
    node('maven-11') {
        timeout(60) {
            checkout scm
            sh 'mvn -B -ntp -Dset.changelist -Dmaven.test.failure.ignore clean install'
            infra.prepareToPublishIncrementals()
            junit 'target/surefire-reports/*.xml'
        }
    }
}
parallel branches, failFast: true
infra.maybePublishIncrementals()
