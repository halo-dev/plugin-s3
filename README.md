# plugin-s3

为 Halo 2.0 提供 S3 协议的对象存储策略，支持阿里云、腾讯云、七牛云等兼容 S3 协议的对象存储服务商

## 使用方法

1. 在 [Releases](https://github.com/halo-sigs/plugin-s3/releases) 下载最新的 JAR 文件。
2. 在 Halo 后台的插件管理上传 JAR 文件进行安装。
3. 进入后台附件管理。
4. 点击右上角的存储策略，在存储策略弹框的右上角可新建 S3 Object Storage 存储策略。
5. 创建完成之后即可在上传的时候选择新创建的 S3 Object Storage 存储策略。

## 配置指南

### Endpoint 访问风格

请根据下方表格中的兼容访问风格选择，若您的服务商不在表格中，请自行查看服务商的 s3 兼容性文档或自行尝试。

> 风格说明：<br/>
> 当Endpoint填写`s3.example.com`时<br/>
> Path Style：SDK将访问`s3.example.com/<bucket-name>/<object-key>`<br/>
> Virtual Hosted Style：SDK将访问`<bucket-name>.s3.example.com/<object-key>`

### Endpoint

此处统一填写**不带** bucket-name 的 Endpoint，SDK 会自动处理访问风格。

想了解 s3 协议的 Endpoint 的配置可在服务商的文档中搜索 s3、Endpoint 或访问域名等关键词，一般与服务商自己的 Endpoint 相同。

> 例如百度云提供 `s3.bj.bcebos.com` 和 `<bucket-name>.s3.bj.bcebos.com` 两种 Endpoint，请填写`s3.bj.bcebos.com`。

### Access Key & Access Secret

与服务商自己 API 的 Access Key 和 Access Secret 相同，详情查看对应服务商的文档。

### Bucket 桶名称

与服务商的控制台中的桶名称一致。

### Region

一般留空即可。

> 若确认过其他配置正确又不能访问，请在服务商的文档中查看并填写英文的 Region，例如 `cn-east-1`。
> 
> Cloudflare 需要填写均为小写字母的 `auto`。

## 部分对象存储服务商兼容性

|服务商|文档|兼容访问风格|兼容性|
| ----- | ---- | ----- | ----- |
|阿里云|https://help.aliyun.com/document_detail/410748.html|Virtual Hosted Style|✅|
|腾讯云|[https://cloud.tencent.com/document/product/436/41284](https://cloud.tencent.com/document/product/436/41284)|Virtual Hosted Style / <br>Path Style|✅|
|七牛云|https://developer.qiniu.com/kodo/4088/s3-access-domainname|Virtual Hosted Style / <br>Path Style|✅|
|百度云|https://cloud.baidu.com/doc/BOS/s/Fjwvyq9xo|Virtual Hosted Style / <br>Path Style|✅|
|京东云| https://docs.jdcloud.com/cn/object-storage-service/api/regions-and-endpoints |Virtual Hosted Style|✅|
|金山云|https://docs.ksyun.com/documents/6761|Virtual Hosted Style|✅|
|青云|https://docsv3.qingcloud.com/storage/object-storage/s3/intro/|Virtual Hosted Style / <br>Path Style|✅|
|网易数帆|[https://sf.163.com/help/documents/89796157866430464](https://sf.163.com/help/documents/89796157866430464)|Virtual Hosted Style|✅|
|Cloudflare|Cloudflare S3 兼容性API<br>[https://developers.cloudflare.com/r2/data-access/s3-api/](https://developers.cloudflare.com/r2/data-access/s3-api/)|Virtual Hosted Style / <br>Path Style|✅|
|自建minio|\-|Path Style|✅|
|华为云|文档未说明是否兼容，工单反馈不保证兼容性，实际测试可以使用|Virtual Hosted Style|❓|
|Ucloud|只支持 8MB 大小的分片，本插件暂不支持<br>[https://docs.ucloud.cn/ufile/s3/s3\_introduction](https://docs.ucloud.cn/ufile/s3/s3_introduction)|\-|❌|
|又拍云|暂不支持 s3 协议|\-|❌|

## 开发环境

```bash
./gradlew build
```

修改 Halo 的配置文件

```yaml
plugin:
  runtime-mode: development # development, deployment
  classes-directories:
    - "build/classes"
    - "build/resources"
  lib-directories:
    - "libs"
  fixedPluginPath:
    - "path/to/plugin-s3"
```

启动 Halo 之后即可在后台插件管理看到此插件。

## 生产构建

```yaml
./gradlew build
```

构建完成之后，可以在 `build/libs` 目录得到插件的 JAR 包，在 Halo 后台的插件管理上传即可。
