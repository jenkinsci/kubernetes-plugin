package org.csanchez.jenkins.plugins.kubernetes;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.model.jackson.UnmatchedFieldTypeModule;
import java.io.IOException;
import java.io.InputStream;

/**
 * Use Jackson for serialization to continue support octal notation `0xxx`.
 *
 * @see io.fabric8.kubernetes.client.utils.Serialization
 * @see io.fabric8.kubernetes.client.utils.KubernetesSerialization
 */
public final class Serialization2 {

    private static final ObjectMapper objectMapper =
            new ObjectMapper(new YAMLFactory().disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));

    static {
        objectMapper.registerModules(new JavaTimeModule(), new UnmatchedFieldTypeModule());
        objectMapper.disable(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE);
        objectMapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));
    }

    private Serialization2() {}

    public static <T extends KubernetesResource> T unmarshal(InputStream is, Class<T> type) throws IOException {
        try {
            return objectMapper.readerFor(type).readValue(is);
        } catch (JsonProcessingException e) {
            throw new IOException("Unable to parse InputStream.", e);
        } catch (IOException e) {
            throw new IOException("Unable to read InputStream.", e);
        }
    }

    @NonNull
    public static String asYaml(@CheckForNull Object model) {
        if (model != null) {
            try {
                return objectMapper.writeValueAsString(model);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
        return "";
    }
}
