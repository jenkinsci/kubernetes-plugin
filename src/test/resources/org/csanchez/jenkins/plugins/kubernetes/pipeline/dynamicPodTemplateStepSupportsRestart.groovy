def call(body) {
  def config = [:]
  body.resolveStrategy = Closure.DELEGATE_FIRST
  body.delegate = config
  milestone()
  def yamlPod = '''
apiVersion: v1
kind: Pod
metadata:
  labels:
    jenkins: agent
spec:
  containers:
  - name: maven-image
    command:
    - cat
    image: busybox
    imagePullPolicy: IfNotPresent
    resources:
      limits:
        cpu: 100m
        memory: 8Mi
      requests:
        cpu: 100m
        memory: 8Mi
    tty: true
  dnsPolicy: ClusterFirst
  restartPolicy: Never
  securityContext: {}
  terminationGracePeriodSeconds: 30
'''
  def yamlPodHash = '#{project.artifactId}-#{project.version}-maven-image'

  // one build per job to ensure tag consistency
  try {
    lock(resource: env.JOB_NAME.split('/')[1],  inversePrecedence: true) {
      milestone()
      podTemplate(label: yamlPodHash, instanceCap: 6,  idleMinutes: 60, cloud: 'dynamicPodTemplateStepSupportsRestart', yaml: yamlPod) {
        node(yamlPodHash) {
          container('maven-image') {
            timeout(20) {
	      println "dynamic pod template step success"
	    }
          }
        }
      }
    }

    println "${JOB_NAME} ${currentBuild.displayName} see ${JOB_URL}"

  }
  catch (e) {
    println e
    println "${JOB_NAME} ${currentBuild.displayName} see ${JOB_URL}"
    throw e
  }
}
