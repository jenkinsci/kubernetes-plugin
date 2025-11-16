properties([
    disableConcurrentBuilds(abortPrevious: true),
    durabilityHint('PERFORMANCE_OPTIMIZED'),
    buildDiscarder(logRotator(numToKeepStr: '5')),
])

def splits
stage('Determine splits') {
    node('maven-21') {
        checkout scm
        splits = splitTests parallelism: count(2), generateInclusions: true, estimateTestsFromFiles: true
    }
}
stage('Tests') {
    def branches = [:]
    branches['failFast'] = true

    for (int i = 0; i < splits.size(); i++) {
        def num = i
        def split = splits[num]
        def index = num + 1
        branches["kind-${index}"] = {
            node('docker') {
                timeout(90) {
                    checkout scm
                    try {
                        writeFile file: (split.includes ? "$WORKSPACE_TMP/includes.txt" : "$WORKSPACE_TMP/excludes.txt"), text: split.list.join("\n")
                        writeFile file: (split.includes ? "$WORKSPACE_TMP/excludes.txt" : "$WORKSPACE_TMP/includes.txt"), text: ''
                        sh './kind.sh -Penable-jacoco -Dsurefire.includesFile="$WORKSPACE_TMP/includes.txt" -Dsurefire.excludesFile="$WORKSPACE_TMP/excludes.txt"'
                        junit 'target/surefire-reports/*.xml'
                        withEnv(['NUM=' + num]) {
                            sh '''
                                for f in $(find . -name jacoco.exec)
                                do
                                    mv "$f" "$(echo "$f" | sed s/jacoco./jacoco-$NUM./)"
                                done
                            '''
                        }
                        if (num == 0) {
                            stash name: 'classes', includes: '**/target/classes/**'
                        }
                        stash name: 'coverage-exec-' + num, allowEmpty: true, includes: '**/target/jacoco*.exec'
                    } finally {
                        dir(env.WORKSPACE_TMP) {
                            if (fileExists('kindlogs/docker-info.txt')) {
                                archiveArtifacts 'kindlogs/'
                            }
                        }
                    }
                }
            }
        }
    }
    branches['jdk25'] = {
        retry(count: 3, conditions: [kubernetesAgent(handleNonKubernetes: true), nonresumable()]) {
            node('maven-25') {
                timeout(60) {
                    checkout scm
                    sh 'mvn --show-version -B -ntp -Dset.changelist -Dmaven.test.failure.ignore clean install'
                    infra.prepareToPublishIncrementals()
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
    }
    parallel branches
    stage('aggregate coverage') {
        node('maven-25') {
            checkout scm
            unstash 'classes'
            for (int i = 0; i < splits.size(); i++) {
                unstash 'coverage-exec-' + i
            }
            sh 'mvn -B -ntp -P merge-jacoco-reports validate'
            recordCoverage(tools: [[parser: 'JACOCO', pattern: '**/jacoco/jacoco.xml']], sourceCodeRetention: 'MODIFIED')
        }
    }
}
// Stage part of the library
infra.maybePublishIncrementals()
