# kubernetes operator leaning
本实验参照 [JAVA OPERATOR SDK](https://javaoperatorsdk.io/)官方实例开发一个operator程序。

## 编译
编译项目代码, crd 文件会在编译过程通过 annotation process 生成, 地址位于 apis/target/classes/META-INF/
```shell
mvn clean package -D maven.test.skip=true 
```
## 部署crd
部署 crd 到 kubernetes 集群, crd 是集群资源, 没有命名空间, 注意 webpages.operator.k8s.huang.org-v1.yml 文件时maven生成的，需要执行 mvn 编译才会有
```shell
kubectl apply -f apis/target/classes/META-INF/fabric8/webpages.operator.k8s.huang.org-v1.yml
```
部署完成 crd 就可以部署 WebPage(cr), 不过此时还没有处理 WebPage(cr) 的控制器(operator也是控制器), 所以部署上去没有任何效果, 仅仅在服务器添加一行记录而已.

## 部署 operator
部署 operator 和部署其他 web 服务没有什么不同, 集群不会和 operator 交互，没有特殊配置, 
operator 启动后自己去调用集群的 api watch 相关资源, 所以 operator 的 pod 需要配置 service account 以及相关的权限.
operator 可以部署在任何命名空间, 这里部署在 webpage-operator 命名空间(写死在 k8s/operator.yaml 中了)。
可以做集群外部署和集群内部署两种方式:
### 集群外部署（idea环境）
idea环境确保 kubernetes 的配置~/.kube/config 是正确的. 在idea中直接启动main函数
### 集群内部署
打包 operator 程序 docker 镜像。这里镜像版本号要和 [k8s/operator.yaml](k8s/operator.yaml) 中配置保持一致
```shell
docker buildx b ./operator/ -t webpage-operator:0.0.1
```
部署镜像服务到集群
```shell
kubectl apply -f k8s/operator.yaml
```
部署后检查部署状态
```shell
kubectl get all -n webpage-operator
```

## 部署 WebPage(cr)
部署用户自定义资源 WebPage, 这里部署在默认的命名空间
```shell
kubectl apply -f k8s/webpage.yaml
```
部署成功后查看 WebPage 已经部署
```shell
kubectl get wb
```
operator 监控到 WebPage 创建会生成一个 configmap(hello-operator-html), 一个 deployment(deployment.apps/hello-operator) 和 一个 service(service/hello-operator).
并且 service 已经通过 nodeport 暴露
```shell
kubectl get all
```
获取 nodeport, 假设是 30816, 通过浏览器访问页面成功显示 [k8s/operator.yaml](k8s/operator.yaml) 中 .spec.html 内容。如果部署在wsl上请用firefox访问， chrome和 edge可能会无法访问
http://localhost:30816/

## 修改 WebPage
修改 [k8s/operator.yaml](k8s/operator.yaml) 中 .spec.html 内容, 重新部署
```shell
kubectl apply -f k8s/webpage.yaml
```
operator 监控到 WebPage 更新, 会更新对应的资源, 再次刷新浏览器, 显示修改成功(通常需要等待一两分钟).

## 清理
删除webpage
```shell
kubectl delete -f k8s/webpage.yaml
```
检查自定义资源已经被删除
```shell
kubectl get wb
```
operator 监控到 WebPage 删除会删除对应的资源. 检查相关联的标准资源对象也被删除
```shell
# deployment, service
kubectl get all
# configmap
kubectl get cm
```
删除operator
```shell
kubectl delete -f k8s/operator.yaml
# 删除crd 
kubectl delete -f apis/target/classes/META-INF/fabric8/webpages.operator.k8s.huang.org-v1.yml
```