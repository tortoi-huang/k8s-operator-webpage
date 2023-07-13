# kubernetes operator leaning
本实验参照 [JAVA OPERATOR SDK](https://javaoperatorsdk.io/)官方实例开发一个operator程序。
## 实验目标
通过部署一个名为 WebPage 的 CustomResourceDefinition 替代部署一个nginx pod、一个service、一个deployment。
## 步骤
1. 实例 CustomResourceDefinition 的资源对象: org.huang.k8s.customresource.WebPage,org.huang.k8s.customresource.WebPageSpec, org.huang.k8s.customresource.WebPageStatus, 这些对象对于存储的资源
2. 实例 Reconciler（org.huang.k8s.reconciler.WebPageReconciler）对象， 当crd被部署时，该对象的实现方法会被controller调用处理crd部署逻辑
3. 实现 EventSourceInitializer（org.huang.k8s.reconciler.WebPageReconciler）监听那些资源变更需要重新部署crd对象关联的内部对象，如deployment、service
4. 编写main函数注册Reconciler 到operator对象上
5. 编译生成可执行jar包并构建docker镜像
6. kubenate上部署CustomResourceDefinition资源，在打包时生成的 target/classes/META-INF/fabric8/webpages.huang.javaoperatorsdk-v1.yml文件
7. kubenate上部署operator对象，参考k8s/operator.yaml， 检查部署没有错误 kubectl get all -n webpage-operator ，  kubectl logs $operatorpodname -n webpage-operator
8. 部署crd对象， 参考 k8s/webpage.yaml, 检查是否全部部署成功  kubectl get all
## bug
修改configmap的数据没有重新部署pod， 看起来operator没有监听到configmap的变更