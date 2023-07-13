package org.huang.k8s;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import org.huang.k8s.reconciler.WebPageReconciler;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class ApiTest {
    String yamlName = "deployment.yaml";

    @Test
    void testApi1() {
        Deployment desiredDeployment =
                ReconcilerUtils.loadYaml(Deployment.class, WebPageReconciler.class, yamlName);
        System.out.println(desiredDeployment);
    }
    @Test
    void testApi2() {
        try (InputStream is = WebPageReconciler.class.getResourceAsStream(yamlName)) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            while (is.read(b) > -1)
                baos.write(b);
            System.out.println(baos.toString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
