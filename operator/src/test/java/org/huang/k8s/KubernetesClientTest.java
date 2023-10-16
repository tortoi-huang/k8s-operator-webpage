package org.huang.k8s;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.StandardHttpRequest;
import io.fabric8.kubernetes.client.utils.Serialization;
import lombok.extern.slf4j.Slf4j;
import org.huang.k8s.customresource.WebPage;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class KubernetesClientTest {

    @Test
    void configMapApply() {
        final KubernetesClient client = new KubernetesClientBuilder().build();

        MixedOperation<WebPage, KubernetesResourceList<WebPage>, Resource<WebPage>> resources = client.resources(WebPage.class);
        WebPage webPage = resources.inNamespace("default").withName("hello-operator").get();

        Map<String, String> data = new HashMap<>();
        data.put("index.html", "configMapApply test");

        ConfigMap configMap =
                new ConfigMapBuilder()
                        .withMetadata(
                                new ObjectMetaBuilder()
                                        .withName("hello-operator-html")
                                        .withNamespace("default")
                                        .withLabels(Map.of("ngsvr", "true"))
                                        .build())
                        .withData(data)
                        .build();
        configMap.addOwnerReference(webPage);

        ConfigMap configMap1 = client.configMaps().inNamespace("default").resource(configMap).get();
        log.debug("configMap:\n {}", Serialization.asYaml(configMap1));
        List<ManagedFieldsEntry> aDefault = configMap1.getMetadata().getManagedFields();
        log.debug("ManagedFields:\n {}", Serialization.asYaml(aDefault));
        client.configMaps().inNamespace("default").resource(configMap).forceConflicts().serverSideApply();
        //client.configMaps().inNamespace("default").resource(configMap).patch();
    }
}
