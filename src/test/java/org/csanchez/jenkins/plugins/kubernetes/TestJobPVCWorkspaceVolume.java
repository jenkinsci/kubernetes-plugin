/*
 * Copyright 2021 Falco Nikolas
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.csanchez.jenkins.plugins.kubernetes;

import static java.util.logging.Level.INFO;
import static org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud.DEFAULT_POD_LABELS;
import static org.csanchez.jenkins.plugins.kubernetes.PodTemplateUtils.substituteEnv;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.csanchez.jenkins.plugins.kubernetes.volumes.workspace.WorkspaceVolume;
import org.jenkinsci.Symbol;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.interceptor.RequirePOST;

import com.google.common.collect.ImmutableMap;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.util.ListBoxModel;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;

@SuppressWarnings("serial")
public class TestJobPVCWorkspaceVolume extends WorkspaceVolume {
    private static final Logger LOGGER = Logger.getLogger(TestJobPVCWorkspaceVolume.class.getName());

    private final String storageClassName;
    private final String requestsSize;
    private final String accessModes;
    private String jobName;

    @DataBoundConstructor
    public TestJobPVCWorkspaceVolume(String storageClassName,
                                 @Nonnull String requestsSize,
                                 String accessModes) {
        this.storageClassName = storageClassName;
        this.requestsSize = requestsSize;
        this.accessModes = accessModes;
    }

    @CheckForNull
    public String getAccessModes() {
        return accessModes;
    }

    @CheckForNull
    public String getRequestsSize() {
        return requestsSize;
    }

    @CheckForNull
    public String getStorageClassName() {
        return storageClassName;
    }

    @Override
    public void processAnnotations(Collection<PodAnnotation> annotations) {
        jobName = annotations.stream() //
                .filter(a -> "job".equals(a.getKey())) //
                .findFirst() //
                .orElseThrow(() -> new IllegalArgumentException("job name not found")) //
                .getValue();
    }

    @Override
    public Volume buildVolume(String volumeName, String podName) {
        return new VolumeBuilder() //
                .withName(volumeName) //
                .withNewPersistentVolumeClaim() //
                .withClaimName(getPVCName()) //
                .withReadOnly(true) //
                .and() //
                .build();
    }

    private String getPVCName() {
        return "pvc-" + jobName.trim().replace(' ', '-').replace('/', '-').toLowerCase();
    }

    @Override
    public PersistentVolumeClaim createVolume(KubernetesClient client, ObjectMeta podMetaData) {
        String namespace = podMetaData.getNamespace();
        String podId = podMetaData.getName();
        String pvcName = getPVCName();
        LOGGER.log(Level.FINE, "Adding workspace volume {0} from pod: {1}/{2}", new Object[] { pvcName, namespace, podId });

        List<PersistentVolumeClaim> pvcs = client.persistentVolumeClaims().list().getItems();
        PersistentVolumeClaim pvc = pvcs.stream().filter(p -> Objects.equals(p.getMetadata().getName(), pvcName)).findFirst().orElse(null);
        if (pvc == null) {
            pvc = new PersistentVolumeClaimBuilder() //
                    .withNewMetadata() //
                        .withName(pvcName) //
                        .withLabels(DEFAULT_POD_LABELS) //
                    .endMetadata() //
                    .withNewSpec() //
                        .withAccessModes(getAccessModesOrDefault()) //
                        .withNewResources() //
                            .withRequests(getResourceMap()) //
                        .endResources() //
                        .withStorageClassName(getStorageClassNameOrDefault()) //
                    .endSpec() //
                    .build();
            pvc = client.persistentVolumeClaims().inNamespace(podMetaData.getNamespace()).create(pvc);
            LOGGER.log(INFO, "Created PVC: {0}/{1}", new Object[] { namespace, pvc.getMetadata().getName() });
        }
        return pvc;
    }

    public String getStorageClassNameOrDefault() {
        if (getStorageClassName() != null) {
            return getStorageClassName();
        }
        return null;
    }

    public String getAccessModesOrDefault() {
        if (getAccessModes() != null) {
            return getAccessModes();
        }
        return "ReadWriteOnce";
    }

    public String getRequestsSizeOrDefault() {
        if (getRequestsSize() != null) {
            return getRequestsSize();
        }
        return "10Gi";
    }

    protected Map<String, Quantity> getResourceMap() {
        ImmutableMap.Builder<String, Quantity> builder = ImmutableMap.<String, Quantity> builder();
        String actualStorage = substituteEnv(getRequestsSizeOrDefault());
        Quantity storageQuantity = new Quantity(actualStorage);
        builder.put("storage", storageQuantity);
        return builder.build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        TestJobPVCWorkspaceVolume that = (TestJobPVCWorkspaceVolume) o;
        return Objects.equals(storageClassName, that.storageClassName) //
                && Objects.equals(requestsSize, that.requestsSize) //
                && Objects.equals(accessModes, that.accessModes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storageClassName, requestsSize, accessModes);
    }

    @Extension
    @Symbol("jobPVC")
    public static class DescriptorImpl extends Descriptor<WorkspaceVolume> {

        private static final ListBoxModel ACCESS_MODES_BOX = new ListBoxModel() //
                .add("ReadWriteOnce") //
                .add("ReadOnlyMany") //
                .add("ReadWriteMany");

        @Override
        public String getDisplayName() {
            return "Per Job Persistent Volume Claim Workspace Volume";
        }

        @RequirePOST
        @Restricted(DoNotUse.class) // stapler only
        public ListBoxModel doFillAccessModesItems() {
            return ACCESS_MODES_BOX;
        }
    }
}
