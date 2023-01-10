package org.csanchez.jenkins.plugins.kubernetes;

// TODO post 2.362 use jenkins.util.SetContextClassLoader
class WithContextClassLoader implements AutoCloseable {

    private final ClassLoader previousClassLoader;

    public WithContextClassLoader(ClassLoader classLoader) {
        this.previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    @Override
    public void close() {
        Thread.currentThread().setContextClassLoader(previousClassLoader);
    }
}
