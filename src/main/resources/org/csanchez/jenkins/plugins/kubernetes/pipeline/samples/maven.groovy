// Build a Maven project using the standard image and Scripted syntax.
// Rather than inline YAML, you could use: yaml: readTrusted('jenkins-pod.yaml')
// Or, to avoid YAML: containers: [containerTemplate(name: 'maven', image: 'maven:3.6.3-jdk-8', command: 'sleep', args: 'infinity')]
podTemplate(yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: maven:3.6.3-jdk-8
    command:
    - sleep
    args:
    - infinity
''') {
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
                <version>2.18.1</version>
            </plugin>
        </plugins>
    </build>
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>4.12</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>1.8</maven.compiler.source>
        <maven.compiler.target>1.8</maven.compiler.target>
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
        container('maven') {
            sh 'mvn -B -ntp -Dmaven.test.failure.ignore verify'
        }
        junit '**/target/surefire-reports/TEST-*.xml'
        archiveArtifacts '**/target/*.jar'
    }
}
