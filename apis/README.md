# kubernetes operator leaning
实现一组自定义资源(CustomResourceDefinition)
## 实验内容
通过编写一个java类WebPage，编译时通过maven插件 crd-generator-apt 生成一个 CustomResourceDefinition 的yaml部署文件

## 结论
1. io.fabric8:crd-generator-apt类库会在maven的编译阶段插入一个 annotationProcessors 扫描使用了io.fabric8.kubernetes.model.annotation相关注解的类生成CustomResourceDefinition的yaml文件到target/classes/META-INF/fabric8/目录下。
2. 类WebPage中 Group和Version 注解是必须的， 其他注解有默认值，其中Plural和crd的name属性默认值是kubernetes统一约定的，crd文件的各个属性的约定参照https://kubernetes.io/docs/tasks/extend-kubernetes/custom-resources/custom-resource-definitions/
