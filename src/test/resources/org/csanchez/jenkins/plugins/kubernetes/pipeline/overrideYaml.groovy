podTemplate(label: 'mergeYaml-parent', yaml: """
spec:
    containers:
    - name: jnlp
    env:
    - name: VAR1
      value: 1
""") {
    podTemplate(label: 'mergeYaml-child',
            yaml: """
    containers:
    - name: jnlp
    env:
    - name: VAR2
      value: 1
""") {
        node('mergeYaml-child'){
            sh '["$VAR1" != "$VAR2"]'
        }
    }
}