podTemplate() {
    node(POD_LABEL) {
        env.THEVAR = "\$string\$with\$dollars"
        echo "from Groovy: ${env.THEVAR}"
        sh 'echo "outside container: $THEVAR"'
        container('jnlp') {
            sh 'echo "inside container: $THEVAR"'
        }
    }
}
