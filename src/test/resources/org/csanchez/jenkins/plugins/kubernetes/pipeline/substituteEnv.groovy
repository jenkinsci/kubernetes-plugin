podTemplate(annotations: [podAnnotation(key: 'hack', value: 'xxx${HOME}xxx')]) {
    node(POD_LABEL) {
        sh ':'
    }
}
