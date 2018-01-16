podTemplate(label: 'pod-anti-affinity',
    containers: [
        containerTemplate(
                name: 'maven',
                image: 'maven:3.3.9-jdk-8-alpine',
                ttyEnabled: true,
                command: 'cat'
        )],
    podAffinities:[
      podAntiAffinity(
        isRequiredDuringSchedulingIgnoredDuringExecution: true,
        podAffinityTerms: '''
          [
           {
              "labelSelector": {
                 "matchExpressions": [
                    {
                       "key": "app",
                       "operator": "In",
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
                                "operator": "In",
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
    ]) {
    node ('pod-anti-affinity') {
        stage('Run maven') {
            container('maven') {
                sh 'mvn -version'
            }
        }
    }
}