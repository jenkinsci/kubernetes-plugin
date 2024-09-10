// Build a Maven project using the standard image and Scripted syntax.
// Rather than inline YAML, you could use: yaml: readTrusted('jenkins-pod.yaml')
// Or, to avoid YAML: containers: [containerTemplate(name: 'maven', image: 'maven:3.6.3-jdk-8', command: 'sleep', args: 'infinity')]
podTemplate(
    agentContainer: 'maven',
    agentInjection: true,
    yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    # In a real Jenkinsfile, it is recommended to pin to a specific version and use Dependabot or Renovate to bump it.
    image: maven:latest
    command:
    - sleep
    args:
    - infinity
    securityContext:
      # maven runs as root by default, it is recommended or even mandatory in some environments (such as pod security admission "restricted") to run as a non-root user.
      runAsUser: 1000
''') {
    retry(count: 2, conditions: [kubernetesAgent(), nonresumable()]) {
        node(POD_LABEL) {
            // or, for example: git 'https://github.com/jglick/simple-maven-project-with-tests'
            writeFile file: 'pom.xml', text: '''
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>sample</groupId>
    <artifactId>sample</artifactId>
    <version>1.0-SNAPSHOT</version>
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
            </plugin>
        </plugins>
    </build>
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.13.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.release>17</maven.compiler.release>
    </properties>
</project>
        '''
            writeFile file: 'src/test/java/sample/SomeTest.java', text: '''
package sample;
public class SomeTest {
    @org.junit.Test
    public void checks() {}
}
        '''
            // Maven needs write access to $HOME/.m2, which it doesn't have in the maven image because only root is a real user.
            sh 'HOME=$WORKSPACE_TMP/maven mvn -B -ntp -Dmaven.test.failure.ignore verify'
            junit '**/target/surefire-reports/TEST-*.xml'
            archiveArtifacts '**/target/*.jar'
        }
    }
}
