# kubernetes operator leaning
本实验参照 [JAVA OPERATOR SDK](https://javaoperatorsdk.io/)官方实例开发一个operator程序。
## 实验目标
编写一个 operator 程序，通过pod方式部署到kubernetes集群中, operator启动后会watch api server中的WebPage资源，如果发现该资源有部署则解析该资源并部署一个nginx pod、一个service、一个deployment。

## 部署
1. kubernetes集群集群中部署apis的 CustomResourceDefinition资源(编译后生成): apis/target/classes/META-INF/fabric8/webpages.operator.k8s.huang.org-v1.yml
2. 在kubernetes集群部署一个operator 的pod及其需要的api server访问权限: k8s/operator.yaml
3. 部署一个CustomResource: k8s/webpage.yaml
4. 使用kubectl检查确认deployment和service部署成功

## 编码实现
1. 实例 CustomResourceDefinition 的资源对象: org.huang.k8s.customresource.WebPage,org.huang.k8s.customresource.WebPageSpec, org.huang.k8s.customresource.WebPageStatus, 这些对象对于存储的资源
2. 实例 Reconciler（org.huang.k8s.reconciler.WebPageReconciler）对象， 当crd被部署时，该对象的实现方法会被controller调用处理crd部署逻辑
3. 实现 EventSourceInitializer（org.huang.k8s.reconciler.WebPageReconciler）监听那些资源变更需要重新部署crd对象关联的内部对象，如deployment、service
4. 编写main函数注册Reconciler 到operator对象上
5. 编译生成可执行jar包并构建docker镜像
6. kubernetes 上部署CustomResourceDefinition资源，在打包时生成的 target/classes/META-INF/fabric8/webpages.huang.javaoperatorsdk-v1.yml文件
7. kubernetes 上部署operator对象，参考k8s/operator.yaml， 检查部署没有错误 kubectl get all -n webpage-operator ，  kubectl logs $operatorpodname -n webpage-operator
8. 部署crd对象， 参考 k8s/webpage.yaml, 检查是否全部部署成功  kubectl get all
## bug
修改configmap的数据没有重新部署pod， 看起来operator没有监听到configmap的变更

# 词汇解析
1. Primary Resource: 主资源，需要operator处理的资源，通常是一个cr资源，如这个例子中的webpage.yaml， 也可以是kubernetes的内置资源如deployment。
2. Secondary Resource: 二级资源， 需要处理达到的状态资源，如这里主资源需要生成的deployment, service， configmap都是二级资源，另外如果主资源部署一个deployment，那么它的二级资源是ReplicatSet
3. Dependent Resource: 表达二级资源之间的依赖关系
4. 