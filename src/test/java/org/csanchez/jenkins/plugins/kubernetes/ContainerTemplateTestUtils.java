package org.csanchez.jenkins.plugins.kubernetes;

/**
 * @author <a href="mailto:kirill.shepitko@gmail.com">Kirill Shepitko</a>
 */
public class ContainerTemplateTestUtils {

    public static ContainerTemplate containerTemplate(String name) {
        return containerTemplate(name, false, false);
    }

    public static ContainerTemplate containerTemplate(String name, boolean slaveImage) {
        return containerTemplate(name, slaveImage, false);
    }

    public static ContainerTemplate containerTemplate(String name, boolean slaveImage, boolean selfRegisteringSlave) {
        ContainerTemplate result = new ContainerTemplate(name, "image");
        result.setSlaveImage(slaveImage);
        result.setSelfRegisteringSlave(slaveImage & selfRegisteringSlave);
        return result;
    }

}
