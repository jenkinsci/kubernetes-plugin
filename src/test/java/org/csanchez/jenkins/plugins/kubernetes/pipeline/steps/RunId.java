package org.csanchez.jenkins.plugins.kubernetes.pipeline.steps;

import java.io.Serializable;

/**
 * Lightweight, serializable reference to a run which can be passed around steps.
 */
public class RunId implements Serializable {
    String name;
    int number;

    RunId(String name, int number) {
        this.name = name;
        this.number = number;
    }

    public String getName() {
        return name;
    }

    public int getNumber() {
        return number;
    }
}
