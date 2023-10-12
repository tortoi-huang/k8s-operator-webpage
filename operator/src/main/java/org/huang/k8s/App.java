package org.huang.k8s;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import org.huang.k8s.http.OperatorHttpServer;
import org.huang.k8s.reconciler.WebPageReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Hello world!
 */
public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    /**
     * 如果本地配置了~/.kube/config， 可以在本地直接启动此程序,不必打包部署到kubernetes集群. KubernetesClient 会读取该配置监听kubernetes集群
     * @param args
     */
    public static void main(String[] args) {
        try {
            //类似kubectl 执行kubernetes api命令的客户端
            KubernetesClient client = new KubernetesClientBuilder().build();

            //Operator封装了controllerManager，configurationService，leaderElectionManager 三个重要的属性
            Operator operator = new Operator();
            //注册当前 operator监视的crd对象， 可以继续注册更多的crd对象,
            //注册流程包括新建一个controller监视当前crd对象
            operator.register(new WebPageReconciler(client));
            operator.start();

            new OperatorHttpServer(8080, operator).start();
        } catch (IOException e) {
            log.error("主线程异常", e);
        }
    }
}
