/**
 * This pipeline executes Selenium tests against Chrome and Firefox, all running in the same Pod but in separate containers
 * and in parallel
 */

podTemplate(yaml: '''
              apiVersion: v1
              kind: Pod
              spec:
                containers:
                - name: maven-firefox
                  image: maven:3.8.1-jdk-8
                  command:
                  - sleep
                  args: 
                  - 99d
                - name: maven-chrome
                  image: maven:3.8.1-jdk-8
                  command:
                  - sleep
                  args: 
                  - 99d
                - name: selenium-hub
                  image: selenium/hub:3.141.59
                - name: selenium-chrome
                  image: selenium/node-chrome:3.141.59
                  env:
                  - name: HUB_PORT_4444_TCP_ADDR
                    value: localhost
                  - name: HUB_PORT_4444_TCP_PORT
                    value: 4444
                  - name: DISPLAY
                    value: :99.0
                  - name: SE_OPTS
                    value: -port 5556
                - name: selenium-firefox
                  image: selenium/node-firefox:3.141.59
                  env:
                  - name: HUB_PORT_4444_TCP_ADDR
                    value: localhost
                  - name: HUB_PORT_4444_TCP_PORT
                    value: 4444
                  - name: DISPLAY
                    value: :98.0
                  - name: SE_OPTS
                    value: -port 5557
''') {

  node(POD_LABEL) {
    stage('Checkout') {
      git 'https://github.com/carlossg/selenium-example.git'
      parallel (
        firefox: {
          container('maven-firefox') {
            stage('Test firefox') {
              sh 'mvn -B clean test -Dselenium.browser=firefox -Dsurefire.rerunFailingTestsCount=5 -Dsleep=0'
            }
          }
        },
        chrome: {
          container('maven-chrome') {
            stage('Test chrome') {
              sh 'mvn -B clean test -Dselenium.browser=chrome -Dsurefire.rerunFailingTestsCount=5 -Dsleep=0'
            }
          }
        }
      )
    }

    stage('Logs') {
      containerLog('selenium-chrome')
      containerLog('selenium-firefox')
    }
  }
}
