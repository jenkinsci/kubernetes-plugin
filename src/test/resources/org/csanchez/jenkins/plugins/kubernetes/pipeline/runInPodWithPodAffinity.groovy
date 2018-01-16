podTemplate(label: 'pod-affinity', containers: [
        containerTemplate(
            name: 'maven',
            image: 'maven:3.3.9-jdk-8-alpine',
            ttyEnabled: true,
            command: 'cat'
        )],
        podAffinities:[
          podAffinity(
            isRequiredDuringSchedulingIgnoredDuringExecution: true,
            podAffinityTerms: '''
              [
               {
                  "labelSelector": {
                     "matchExpressions": [
                        {
                           "key": "app",
                           "operator": "NotIn",
                           "values": [
                              "jenkins-master"
                           ]
                        }
                     ]
                  },
                  "topologyKey": "failure-domain.beta.kubernetes.io/zone"
               }
            ]
            ''',
            isPreferredDuringSchedulingIgnoredDuringExecution: true,
            weightedPodAffinityTerms: '''
              [
                {
                  "weight": 100,
                  "podAffinityTerm": 
                    {
                       "labelSelector": {
                            "matchExpressions": [
                                {
                                    "key": "app",
                                    "operator": "NotIn",
                                    "values": [
                                        "mypod"
                                    ]
                                }
                            ]
                        },
                        "topologyKey": "kubernetes.io/hostname"
                    } 
                }
              ]
            '''
          )
        ]
    ) {
    node ('pod-affinity') {
        stage('Run maven') {
            container('maven') {
                sh 'mvn -version'
            }
        }
    }
}