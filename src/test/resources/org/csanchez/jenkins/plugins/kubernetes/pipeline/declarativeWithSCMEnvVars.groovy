pipeline {
    agent any
    stages {
        stage("foo") {
            steps {
                echo "GIT_COMMIT is ${env.GIT_COMMIT}"
            }
        }
    }
}
