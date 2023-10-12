package org.huang.k8s.customresource;

import io.fabric8.kubernetes.api.model.Namespaced;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.model.annotation.Group;
import io.fabric8.kubernetes.model.annotation.Plural;
import io.fabric8.kubernetes.model.annotation.ShortNames;
import io.fabric8.kubernetes.model.annotation.Version;

/**
 * 编译时，maven的插件crd-generator-apt会扫面包含这些注解的对象生成一个 CustomResourceDefinition yaml文件。<br/>
 * 其中 metadata.name为 Plural + . + Group;
 * spec.names.kind为 类名称，不含包名
 * spec.group 为注解 @Group 内容，该注解为必须，其他注解有默认值
 */
@Group("operator.k8s.huang.org")
@ShortNames("wb")
// Plural是 CustomResourceDefinition.metadata.name的前缀，默认是CustomResourceDefinition.spec.names.kind的小写复数形式，这里可以修改
@Plural("webpages")
@Version("v1")
public class WebPage extends CustomResource<WebPageSpec, WebPageStatus>
        implements Namespaced {

    @Override
    public String toString() {
        return "WebPage{" +
                "spec=" + spec +
                ", status=" + status +
                '}';
    }
}