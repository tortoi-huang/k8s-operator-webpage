package org.huang.k8s;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.junit.AbstractOperatorExtension;
import io.javaoperatorsdk.operator.junit.LocallyRunOperatorExtension;
import org.huang.k8s.customresource.WebPage;
import org.huang.k8s.customresource.WebPageSpec;
import org.huang.k8s.reconciler.WebPageReconciler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.awaitility.Awaitility.await;
import static org.huang.k8s.reconciler.WebPageReconciler.*;

/**
 * Unit test for simple App.
 */
public class AppTest {
    static final Logger log =
            LoggerFactory.getLogger(AppTest.class);
    public static final String TEST_PAGE = "test-page";
    public static final String TITLE1 = "Hello Operator World";
    public static final String TITLE2 = "Hello Operator World Title 2";
    public static final int WAIT_SECONDS = 20;
    public static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    // 读取 ~/.kube/config
    static final KubernetesClient client = new KubernetesClientBuilder().build();

    @RegisterExtension
    AbstractOperatorExtension operator = LocallyRunOperatorExtension.builder()
            .waitForNamespaceDeletion(false)
            .withReconciler(new WebPageReconciler(client))
            .build();


    /**
     * 这里会真实的kubernetes集群部署，读取 ~/.kube/config
     * 这会在本地运行一个operator程序(集群外部)，watch kubernetes api server, 如果发现有webPage对象变更则会执行 WebPageReconciler逻辑
     */
    @Test
    void testAddingWebPage() {
        log.debug("testAddingWebPage start");

        final var webPage = createWebPage(TITLE1);
        log.debug("origin resource:\n{}", Serialization.asYaml(webPage));

        log.warn("--------------------------create--------------------------");
        operator.create(webPage);
        await()
                .atMost(Duration.ofSeconds(WAIT_SECONDS))
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(
                        () -> {
                            try {
                                var actual = operator.get(WebPage.class, TEST_PAGE);
                                log.debug("actual resource:\n{}", Serialization.asYaml(webPage));
                                var deployment = operator.get(Deployment.class, deploymentName(webPage));
                                log.debug("actual resource deployment:\n{}", Serialization.asYaml(deployment));
                                assertThat(actual.getStatus()).isNotNull();
                                assertThat(actual.getStatus().getAreWeGood()).isTrue();
                                assertThat(deployment.getSpec().getReplicas())
                                        .isEqualTo(deployment.getStatus().getReadyReplicas());
                            } catch (Exception e) {
                                // 这里不能抛出异常，因为这个lambda代码会调用多次（间隔pollInterval），
                                // 直到成功或者超时（atMost）结束，如果第一次就抛出异常则立刻结束。
                                // 以最后一次运行结果为准
                                fail("run exception");
                                throw new RuntimeException(e);
                            }
                        });
        assertThat(httpGetForWebPage(webPage)).contains(TITLE1);

        log.warn("--------------------------replace--------------------------");
        operator.replace(createWebPage(TITLE2));
        await().atMost(Duration.ofSeconds(WAIT_SECONDS))
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    ConfigMap configMap = operator.get(ConfigMap.class, configMapName(webPage));
                    assertThat(configMap.getData().get(INDEX_HTML)).isNotNull().contains(TITLE2);
                    String page = httpGetForWebPage(webPage);
                    assertThat(page).isNotNull().contains(TITLE2);
                });

        log.warn("--------------------------delete--------------------------");
        operator.delete(createWebPage(TITLE2));
        await().atMost(Duration.ofSeconds(WAIT_SECONDS))
                .pollInterval(POLL_INTERVAL)
                .untilAsserted(() -> {
                    Deployment deployment = operator.get(Deployment.class, deploymentName(webPage));
                    assertThat(deployment).isNull();
                });
    }

    String httpGetForWebPage(WebPage webPage) {
        try (
                LocalPortForward portForward =
                        client.services().inNamespace(webPage.getMetadata().getNamespace())
                                .withName(serviceName(webPage)).portForward(80);
        ) {
            HttpClient httpClient =
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request =
                    HttpRequest.newBuilder().GET()
                            .uri(new URI("http://localhost:" + portForward.getLocalPort())).build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
        } catch (Exception e) {
            log.error("httpGetForWebPage: ", e);
            return null;
        }
    }

    WebPage createWebPage(String title) {
        WebPage webPage = new WebPage();
        webPage.setMetadata(new ObjectMeta());
        webPage.getMetadata().setName(TEST_PAGE);
        webPage.getMetadata().setNamespace(operator.getNamespace());
        webPage.setSpec(new WebPageSpec());
        webPage
                .getSpec()
                .setHtml(
                        "<html>\n"
                                + "      <head>\n"
                                + "        <title>" + title + "</title>\n"
                                + "      </head>\n"
                                + "      <body>\n"
                                + "        Hello World! \n"
                                + "      </body>\n"
                                + "    </html>");

        return webPage;
    }
}
