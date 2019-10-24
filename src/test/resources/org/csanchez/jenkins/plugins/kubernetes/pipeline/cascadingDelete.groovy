podTemplate(
    podRetention: never(),
    idleMinutes: 0,
    yaml: '''
apiVersion: v1
kind: Pod
spec:
  containers:
    - name: kubectl
      image: bitnami/kubectl:1.12
      stdin: true
      tty: true
      command:
        - '/bin/sh'
      args:
        - -c
        - cat
      env:
        - name: HUB_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: HUB_UID
          valueFrom:
            fieldRef:
              fieldPath: metadata.uid
        - name: HUB_IP
          valueFrom:
            fieldRef:
              fieldPath: status.podIP
  serviceAccountName: jenkins
  securityContext:
    runAsUser: 1000
    fsGroup: 1000
''') {
    node(POD_LABEL) {
        container('kubectl') {
            sh('''\
                deploy=$(
                kubectl create -o name -f - <<EOF
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: cascading-delete
                  labels:
                    app: cascading-delete
                  ownerReferences:
                  - apiVersion: v1
                    kind: Pod
                    controller: true
                    blockOwnerDeletion: true
                    name: $HUB_NAME
                    uid: $HUB_UID
                spec:
                  revisionHistoryLimit: 0
                  replicas: 2
                  selector:
                    matchLabels:
                      app: cascading-delete
                  template:
                    metadata:
                      labels:
                        app: cascading-delete
                    spec:
                      containers:
                      - name: ubuntu
                        image: ubuntu
                        command:
                          - '/bin/sh'
                        args:
                          - -c
                          - 'sleep infinity'
                      nodeSelector:
                        kubernetes.io/os: linux
                EOF
                )
                kubectl rollout status "$deploy"
            '''.stripIndent())
        }
    }
    node(POD_LABEL) {
        container('kubectl') {
            sh 'while kubectl get deploy cascading-delete; do sleep 1; done'
        }
    }
}
