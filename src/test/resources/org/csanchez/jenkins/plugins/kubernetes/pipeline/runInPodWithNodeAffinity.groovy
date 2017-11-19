podTemplate(label: 'node-affinities', containers: [
        containerTemplate(
                name: 'maven',
                image: 'maven:3.3.9-jdk-8-alpine',
                ttyEnabled: true,
                command: 'cat'
        )],
        podAffinities:[
          nodeAffinity(
            isRequiredDuringSchedulingIgnoredDuringExecution: true,
            nodeSelectorTerms: '''
              [
                {
                  "matchExpressions": 
                    [{
                      "key": "kubernetes.io/hostname",
                      "operator": "NotIn",
                      "values": [
                        "kube"
                      ]
                    }] 
                }
              ]
            ''',
            isPreferredDuringSchedulingIgnoredDuringExecution: true,
            preferredTerms: '''
              [
                {
                  "weight": 1,
                  "preference": 
                    {
                       "matchExpressions": 
                        [{
                          "key": "kubernetes.io/hostname",
                          "operator": "NotIn",
                          "values": [
                            "kube"
                          ]
                        }] 
                    } 
                }
              ]
            '''
          )
        ]
    ) {
    node ('node-affinities') {
        stage('Run maven') {
            container('maven') {
                sh 'mvn -version'
            }
        }
    }
}