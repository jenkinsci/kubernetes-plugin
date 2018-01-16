podTemplate(label: 'mixed-affinities', containers: [
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
            '''
          ),
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
                              "mypod"
                           ]
                        }
                     ]
                  },
                  "topologyKey": "kubernetes.io/hostname"
               }
            ]
            '''
          )
        ]
    ) {
    node ('mixed-affinities') {
        stage('Run maven') {
            container('maven') {
                sh 'mvn -version'
            }
        }
    }
}