# kubernetes operator leaning
本实验参照 [JAVA OPERATOR SDK](https://javaoperatorsdk.io/)官方实例开发一个operator程序。
实现一个operator程序，只需要能够调用 api service提供的api监控和更改资源即可，不一定需要使用特定的api.
## 实验目标
编写一个 operator 程序，通过pod方式部署到kubernetes集群中, operator启动后会watch api server中的WebPage资源，如果发现该资源有部署则解析该资源并部署一个nginx pod、一个service、一个deployment。
operator 会通过以下url 访问api server
https://${apiserver}:443/apis/${crd.spec.group}/${crd.spec.versions.name}/${crd.spec.names.kind}?resourceVersion=0&timeoutSeconds=600&allowWatchBookmarks=true&watch=true
假设api serverip为10.96.0.1，则watch地址为:  
https://10.96.0.1:443/apis/operator.k8s.huang.org/v1/webpages?resourceVersion=0&timeoutSeconds=600&allowWatchBookmarks=true&watch=true

## 编码实现
1. 创建KubernetesClient实例， 该实例用于访问 api server，并且提供了配置发现的功能参见下方[部署](#deployment)。 api server 提供了restfull api，任何方式调用这些api都可以,只是要自己去处理账号权限配置。
2. 新建一个 io.javaoperatorsdk.operator.Operator 对象，该对象负责监听api server上的对象变化，并将监听时间转发到合适的处理程序,多个示例运行时还会做选举主节点等额外工作. 可以理解为轮询(实际watch不是轮询)api server 的restful api 发现变化就选择合适的程序处理变化.
3. 新建一个 WebPageReconciler， 通过实现的接口来实现处理新增、修改和删除的逻辑，operator监控到事件会立刻调用次类的方法，没有事件则会每10秒调用一次，检查二级资源的状态。 
   1. 实现 Reconciler 接口，表示这个一个主资源控制器，通常是一个crd。
   2. 实现 EventSourceInitializer 接口， 表示哪些资源变更事件需要通知当前 Reconciler 处理, 这里需要注册所有的二级资源（deployment/service/configmap）
   3. 实现 Cleaner 接口， 该接口实现删除资源时的逻辑，默认会在主资源上添加一个finalizers字段，如：
      ```yaml
      kind: WebPage
      metadata:
          finalizers:
          - webpages.operator.k8s.huang.org/finalizer
      ```
      在删除在api server 收到删除指令(kubectl delete)时不会立刻删除改资源，而是添加一个metadata.deletionTimestamp属性, 等待operator watch到该事件 转发给 Cleaner 接口去执行删除动作. 
      如果没有实现该接口则 operator不处理删除，完全由api server处理。 
      级联删除二级资源是由二级资源是由kubernetes 完成的与 operator 和 Cleaner 接口无关， 该功能是在二级资源上添加一个指向主资源的属性 ownerReferences 如:
      ```yaml
      kind: Service
      metadata:
        labels:
          ngsvr: "true"
        name: hello-operator
        ownerReferences:
        - apiVersion: operator.k8s.huang.org/v1
          kind: WebPage
          name: hello-operator
          uid: db00d12e-be6b-4f2d-9a17-cf42932a8af6
      ```
4. 在 Operator 对象上注册 WebPageReconciler， 实现 Operator会监控该Reconciler关联的cr对象，并将监控的时间转发给该 Reconciler 处理.
5. 运行一个web程序提供健康检查探针,一个 operator 程序不必是一个web程序, 这种情况要使用其他方式实现健康检查探针.

## deployment
可以做集群内部署和集群外部署两种方式:
1. 集群外部署， 在idea中直接执行main方法， KubernetesClient 查找本地 ~/.kube/config 通过这个配置访问集群， 其他集群外部署方式原理也一样
2. 集群内部署， 需要为pod配置service account 以及api访问权限，KubernetesClient会获取 service account来访问集群. 参照 k8s/operator.yaml

# 词汇解析
1. Primary Resource: 主资源，需要operator处理的资源，通常是一个cr资源，如这个例子中的webpage.yaml， 也可以是kubernetes的内置资源如deployment。
2. Secondary Resource: 二级资源， 需要处理达到的状态资源，如这里主资源需要生成的deployment, service， configmap都是二级资源，另外如果主资源部署一个deployment，那么它的二级资源是ReplicatSet
3. Dependent Resource: 表达二级资源之间的依赖关系