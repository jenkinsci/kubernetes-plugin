podTemplate(yaml: """
spec:
    containers:
    - name: jnlp
    env:
    - name: VAR1
      value: 1
""") {
    podTemplate(yaml: """
    containers:
    - name: jnlp
    env:
    - name: VAR2
      value: 1
""") {
        node(POD_LABEL){
            sh '["$VAR1" != "$VAR2"]'
        }
    }
}