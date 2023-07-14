package org.huang.k8s.reconciler;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.ReconcilerUtils;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.event.rate.RateLimited;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.huang.k8s.customresource.WebPage;
import org.huang.k8s.customresource.WebPageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * crd对象 huang.javaoperatorsdk/WebPage处理逻辑
 */
@RateLimited(maxReconciliations = 2, within = 3)
@ControllerConfiguration
public class WebPageReconciler implements Reconciler<WebPage>, EventSourceInitializer<WebPage> {
    private static final Logger log = LoggerFactory.getLogger(WebPageReconciler.class);

    /**
     * 为部署的 deployment/pod/service添加一个label
     */
    public static final String SELECTOR = "ngsvr";

    /**
     * nginx首页文件名称，内容保持在configmap中
     */
    public static final String INDEX_HTML = "index.html";

    /**
     * 类似kubectl 执行kubernetes api命令的客户端
     */
    private final KubernetesClient kubernetesClient;
    private final Map<String, String> label;

    public WebPageReconciler(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.label = new HashMap<>();
        this.label.put(SELECTOR, "true");
    }

    /**
     * 此方法会在kubectl 调用一个WebPage crd操作api时拦截处理,
     * 当crd依赖的configmap/deployment/service等资源被修改同样会触发此方法
     * @param webPage the resource that has been created or updated
     * @param context the context with which the operation is executed
     * @return 更新的结果
     * @throws Exception
     */
    @Override
    public UpdateControl<WebPage> reconcile(WebPage webPage, Context<WebPage> context) throws Exception {
        //获取当前部署的namespace
        String ns = webPage.getMetadata().getNamespace();
        String configMapName = configMapName(webPage);
        String deploymentName = deploymentName(webPage);
        log.info("reconcile {} in {}", webPage, ns);

        ConfigMap desiredHtmlConfigMap = makeDesiredHtmlConfigMap(ns, configMapName, webPage);
        Deployment desiredDeployment =
                makeDesiredDeployment(webPage, deploymentName, ns, configMapName);
        Service desiredService = makeDesiredService(webPage, ns, desiredDeployment);

        // 查询当前crd定义的configmap,
        var previousConfigMap = context.getSecondaryResource(ConfigMap.class).orElse(null);
        if (!match(desiredHtmlConfigMap, previousConfigMap)) {
            log.info(
                    "Creating or updating ConfigMap {} in {}",
                    desiredHtmlConfigMap.getMetadata().getName(),
                    ns);
            kubernetesClient.configMaps().inNamespace(ns).resource(desiredHtmlConfigMap)
                    .createOrReplace();
        }

        // 查询当前crd定义的service
        var existingService = context.getSecondaryResource(Service.class).orElse(null);
        if (!match(desiredService, existingService)) {
            log.info(
                    "Creating or updating service {} in {}",
                    desiredService.getMetadata().getName(),
                    ns);
            kubernetesClient.services().inNamespace(ns).resource(desiredService).createOrReplace();
        }

        // 查询当前crd定义的deployment
        var existingDeployment = context.getSecondaryResource(Deployment.class).orElse(null);
        if (!match(desiredDeployment, existingDeployment)) {
            log.info(
                    "Creating or updating Deployment {} in {}",
                    desiredDeployment.getMetadata().getName(),
                    ns);
            kubernetesClient.apps().deployments().inNamespace(ns).resource(desiredDeployment)
                    .createOrReplace();
        }

        //创建crd对象的状态，这里简单的根据configmap状态来确定
        webPage.setStatus(createStatus(desiredHtmlConfigMap.getMetadata().getName()));

        return UpdateControl.patchStatus(webPage);
    }

    /**
     * FIXME 似乎修改configmap没有效果
     * 注册controller监视的事件，当这里注册的事件发生会通知到controller
     *
     * @param context a {@link EventSourceContext} providing access to information useful to event
     *                sources
     * @return
     */
    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<WebPage> context) {
        var configMapEventSource =
                new InformerEventSource<>(InformerConfiguration.from(ConfigMap.class, context)
                        .withLabelSelector(SELECTOR)
                        .build(), context);
        var deploymentEventSource =
                new InformerEventSource<>(InformerConfiguration.from(Deployment.class, context)
                        .withLabelSelector(SELECTOR)
                        .build(), context);
        var serviceEventSource =
                new InformerEventSource<>(InformerConfiguration.from(Service.class, context)
                        .withLabelSelector(SELECTOR)
                        .build(), context);
        return EventSourceInitializer.nameEventSources(configMapEventSource, deploymentEventSource,
                serviceEventSource);
    }

    /**
     * 对比两个deployment是否相同
     *
     * @param desiredDeployment
     * @param deployment
     * @return
     */
    private boolean match(Deployment desiredDeployment, Deployment deployment) {
        if (deployment == null) {
            return false;
        } else {
            return desiredDeployment.getSpec().getReplicas().equals(deployment.getSpec().getReplicas()) &&
                    desiredDeployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage()
                            .equals(
                                    deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        }
    }

    /**
     * 对比两个service是否相同
     *
     * @param desiredService
     * @param service
     * @return
     */
    private boolean match(Service desiredService, Service service) {
        if (service == null) {
            return false;
        }
        return desiredService.getSpec().getSelector().equals(service.getSpec().getSelector());
    }

    /**
     * 对比两个configmap是否相同
     *
     * @param desiredHtmlConfigMap
     * @param existingConfigMap
     * @return
     */
    private boolean match(ConfigMap desiredHtmlConfigMap, ConfigMap existingConfigMap) {
        if (existingConfigMap == null) {
            return false;
        } else {
            return desiredHtmlConfigMap.getData().equals(existingConfigMap.getData());
        }
    }

    /**
     * 根据crd构建一个service对象
     *
     * @param webPage
     * @param ns
     * @param desiredDeployment
     * @return
     */
    private Service makeDesiredService(WebPage webPage, String ns, Deployment desiredDeployment) {
        Service desiredService = ReconcilerUtils.loadYaml(Service.class, getClass(), "service.yaml");
        desiredService.getMetadata().setName(serviceName(webPage));
        desiredService.getMetadata().setNamespace(ns);
        desiredService.getMetadata().setLabels(label);
        desiredService
                .getSpec()
                .setSelector(desiredDeployment.getSpec().getTemplate().getMetadata().getLabels());

        //添加一些元数据位于deployment文件的 metadata下面，如uid/apiversion/name等属性
        desiredService.addOwnerReference(webPage);
        return desiredService;
    }

    /**
     * 根据crd构建一个deployment
     *
     * @param webPage
     * @param deploymentName
     * @param ns
     * @param configMapName
     * @return
     */
    private Deployment makeDesiredDeployment(WebPage webPage, String deploymentName, String ns,
                                             String configMapName) {

        /**
         * 加载当前类目录（包）下的deployment.yaml模板文件
         */
        Deployment desiredDeployment =
                ReconcilerUtils.loadYaml(Deployment.class, getClass(), "deployment.yaml");
        desiredDeployment.getMetadata().setName(deploymentName);
        desiredDeployment.getMetadata().setNamespace(ns);
        desiredDeployment.getMetadata().setLabels(label);
        desiredDeployment.getSpec().getSelector().getMatchLabels().put("app", deploymentName);
        desiredDeployment.getSpec().getTemplate().getMetadata().getLabels().put("app", deploymentName);
        desiredDeployment
                .getSpec()
                .getTemplate()
                .getSpec()
                .getVolumes()
                .get(0)
                //修改configmap挂载的key， 将configmap中 index.html的内容挂载到磁盘目录
                .setConfigMap(new ConfigMapVolumeSourceBuilder().withName(configMapName).build());

        //添加一些元数据位于deployment文件的 metadata下面，如uid/apiversion/name等属性
        desiredDeployment.addOwnerReference(webPage);
        return desiredDeployment;
    }

    /**
     * 根据crd构建一个configmap 包含label信息和 index内容
     *
     * @param ns            namespace
     * @param configMapName e
     * @param webPage       e
     * @return configmap
     */
    private ConfigMap makeDesiredHtmlConfigMap(String ns, String configMapName, WebPage webPage) {
        Map<String, String> data = new HashMap<>();
        data.put(INDEX_HTML, webPage.getSpec().getHtml());

        ConfigMap configMap =
                new ConfigMapBuilder()
                        .withMetadata(
                                new ObjectMetaBuilder()
                                        .withName(configMapName)
                                        .withNamespace(ns)
                                        .withLabels(label)
                                        .build())
                        .withData(data)
                        .build();
        configMap.addOwnerReference(webPage);
        return configMap;
    }

    /**
     * 根据配置的名称生成一个deployment的名称
     *
     * @param crd crd对象
     * @return deployment名称
     */
    public static String deploymentName(WebPage crd) {
        return crd.getMetadata().getName();
    }

    /**
     * 根据crd的名称生成一个service的名称
     *
     * @param crd crd对象
     * @return service名称
     */
    public static String serviceName(WebPage crd) {
        return crd.getMetadata().getName();
    }

    /**
     * 根据crd的名称生成一个configmap名称
     *
     * @param crd crd对象
     * @return configmap名称
     */
    public static String configMapName(WebPage crd) {
        return crd.getMetadata().getName() + "-html";
    }

    public WebPageStatus createStatus(String configMapName) {
        WebPageStatus status = new WebPageStatus();
        status.setHtmlConfigMap(configMapName);
        status.setAreWeGood(true);
        status.setErrorMessage(null);
        return status;
    }
}
