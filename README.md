# plugin-s3

为 Halo 2.0 提供 S3 协议的对象存储策略，支持阿里云、腾讯云、七牛云等兼容 S3 协议的对象存储服务商

## 使用方法

1. 下载，目前提供以下两个下载方式：
    - GitHub Releases：访问 [Releases](https://github.com/halo-dev/plugin-s3/releases) 下载 Assets 中的 JAR 文件。
    - Halo 应用市场：<https://halo.run/store/apps/app-Qxhpp>
2. 安装，插件安装和更新方式可参考：<https://docs.halo.run/user-guide/plugins>
3. 配置存储策略：
   * 在 Halo 后台管理系统中，点击左侧“附件”导航至附件管理页面，点击右上角的存储策略。
   * 在存储策略管理界面中，您可以新建和编辑 S3 Object Storage 存储策略。
4. 上传到存储策略：
   * 在附件页面中点击上传按钮，选择好存储策略后上传文件即可上传到对应的对象存储中。
   * 在 Halo 2.11 以上版本中可在 Halo 设置界面中设定文章附件、头像等的默认存储策略。
5. 使用“关联 S3 文件”功能：
   * 在左侧侧边导航栏中，点击工具，再点击 S3 关联。
   * 在此界面中，您可以浏览并选择已在对象存储中但不是通过 Halo 上传的文件，关联后会在 Halo 生成相应的附件记录。这些文件现在可以方便地在 Halo 中管理和使用。
6. 使用“解除 S3 关联”功能：
   * 在附件页面中，找到由本插件管理的附件记录，点击更多操作（右侧的三个点）按钮，然后点击“解除 S3 关联”按钮。
   * 此操作将仅删除 Halo 中的附件记录，而不会实际删除对象存储中的文件。如需恢复请使用“关联 S3 文件”功能。

## 配置指南

### Bucket 桶名称

一般与服务商控制台中的空间名称一致。

> 注意部分服务商 s3 空间名 ≠ 空间名称，若出现“Access Denied”报错可检查 Bucket 是否正确。
>
> 可通过 S3Browser 查看桶列表，七牛云也可在“开发者平台-对象存储-空间概览-s3域名”中查看 s3 空间名。

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

### Region

一般留空即可。

> 若确认过其他配置正确又不能访问，请在服务商的文档中查看并填写英文的 Region，例如 `cn-east-1`。
> 
> Cloudflare 需要填写均为小写字母的 `auto`。

### 上传目录

上传到对象存储的目录，前后`/`可省略，例如`/halo`和`halo`是等价的。

支持的占位符有：
* `${uuid-with-dash}`：带有`-`的 UUID
* `${uuid-no-dash}`：不带`-`的 UUID
* `${timestamp-sec}`：秒时间戳（10位时间戳）
* `${timestamp-ms}`：毫秒时间戳（13位时间戳）
* `${year}`：年份
* `${month}`：月份（两位数）
* `${day}`：日期（两位数）
* `${weekday}`：星期几，1-7
* `${hour}`：小时（24小时制，两位数）
* `${minute}`：分钟（两位数）
* `${second}`：秒（两位数）
* `${millisecond}`：毫秒（三位数）
* `${random-alphabetic:X}`：随机的小写英文字母，长度为`X`，例如`${random-alphabetic:5}`会生成`abcde`。
* `${random-num:X}`：随机的数字，长度为`X`，例如`${random-num:5}`会生成`12345`。
* `${random-alphanumeric:X}`：随机的小写英文字母和数字，长度为`X`，例如`${random-alphanumeric:5}`会生成`abc12`。

> **示例**：<br/>
> * `${year}/${month}/${day}/${random-alphabetic:1}`会放在`2023/12/01/a`。<br/>
> * `halo/${uuid-no-dash}`会放在`halo/123E4567E89B12D3A456426614174000`。

### 上传时重命名文件方式
* **保留原文件名：** 使用上传时的文件名。
* **自定义：** 使用`自定义文件名模板`中填写的模板，上传时替换相应占位符作后作为文件名。
* **使用 UUID：** 上传时会自动重命名为随机的 UUID。
* **使用毫秒时间戳：** 上传时会自动重命名为毫秒时间戳（13位时间戳）。
* **使使用原文件名 + 随机字母：** 上传时会自动重命名为原文件名 + 随机的小写英文字母，长度请在`随机字母长度`中设置。
* **使用日期 + 随机字母：** 上传时会自动重命名为日期 + 随机的小写英文字母，例如 `2023-12-01-abcdefgh.png`。
* **使用日期时间 + 随机字母：** 上传时会自动重命名为日期时间 + 随机的小写英文字母，例如 `2023-12-01T09:30:01-abcdef.png`。
* **使用随机字母：** 上传时会自动重命名为随机的小写英文字母，长度请在`随机字母长度`中设置。

### 随机字母长度

仅当`上传时重命名文件方式`为`使用原文件名 + 随机字母`或`使用日期 + 随机字母`或`使用日期时间 + 随机字母`或`使用随机字母`时出现，用于设置随机字母的长度。

### 自定义文件名模板

仅当`上传时重命名文件方式`为`自定义`时出现，用于设置自定义文件名模板。

支持的占位符有：
* `${origin-filename}`：原文件名
* `${uuid-with-dash}`：带有`-`的 UUID
* `${uuid-no-dash}`：不带`-`的 UUID
* `${timestamp-sec}`：秒时间戳（10位时间戳）
* `${timestamp-ms}`：毫秒时间戳（13位时间戳）
* `${year}`：年份
* `${month}`：月份（两位数）
* `${day}`：日期（两位数）
* `${weekday}`：星期几，1-7
* `${hour}`：小时（24小时制，两位数）
* `${minute}`：分钟（两位数）
* `${second}`：秒（两位数）
* `${millisecond}`：毫秒（三位数）
* `${random-alphabetic:X}`：随机的小写英文字母，长度为`X`，例如`${random-alphabetic:5}`会生成`abcde`。
* `${random-num:X}`：随机的数字，长度为`X`，例如`${random-num:5}`会生成`12345`。
* `${random-alphanumeric:X}`：随机的小写英文字母和数字，长度为`X`，例如`${random-alphanumeric:5}`会生成`abc12`。

> **示例**：<br/>
> 当原始文件名为`image.png`时<br/>
> * `${origin-filename}-${uuid-with-dash}`会生成`image-123E4567-E89B-12D3-A456-426614174000.png`。<br/>
> * `${year}-${month}-${day}T${hour}:${minute}:${second}-${random-alphanumeric:5}`会生成`2023-12-01T09:30:01-abc12.png`。<br/>
> * `${uuid-no-dash}_file_${random-alphabetic:5}`会生成`123E4567E89B12D3A456426614174000_file_abcde.png`。<br/>
> * `halo_${origin-filename}_${random-num:3}`会生成`halo_image_123.png`。

### 重复文件名处理方式

* **加随机字母数字后缀：** 如遇重名，会在文件名后加上4位的随机字母数字后缀，例如`image.png`会变成`image_abc1.png`。
* **加随机字母后缀：** 如遇重名，会在文件名后加上4位的随机字母后缀，例如`image.png`会变成`image_abcd.png`。
* **报错不上传** 如遇重名，会放弃上传，并在用户界面提示 Duplicate filename 错误。

## 部分对象存储服务商兼容性

|服务商|文档|兼容访问风格|兼容性|
| ----- | ---- | ----- | ----- |
|阿里云|<https://help.aliyun.com/document_detail/410748.html>|Virtual Hosted Style|✅|
|腾讯云|<https://cloud.tencent.com/document/product/436/41284>|Virtual Hosted Style / <br>Path Style|✅|
|七牛云|<https://developer.qiniu.com/kodo/4088/s3-access-domainname>|Virtual Hosted Style / <br>Path Style|✅|
|百度云|<https://cloud.baidu.com/doc/BOS/s/xjwvyq9l4>|Virtual Hosted Style / <br>Path Style|✅|
|京东云|<https://docs.jdcloud.com/cn/object-storage-service/api/regions-and-endpoints>|Virtual Hosted Style|✅|
|金山云|<https://docs.ksyun.com/documents/6761>|Virtual Hosted Style|✅|
|青云|<https://docsv3.qingcloud.com/storage/object-storage/s3/intro/>|Virtual Hosted Style / <br>Path Style|✅|
|网易数帆|<https://sf.163.com/help/documents/89796157866430464>|Virtual Hosted Style|✅|
|Cloudflare|<https://developers.cloudflare.com/r2/data-access/s3-api/>|Virtual Hosted Style / <br>Path Style|✅|
| Oracle Cloud |<https://docs.oracle.com/en-us/iaas/Content/Object/Tasks/s3compatibleapi.htm>|Virtual Hosted Style / <br>Path Style|✅|
|又拍云|<https://help.upyun.com/knowledge-base/aws-s3%e5%85%bc%e5%ae%b9/>|Virtual Hosted Style / <br>Path Style|✅|
|自建minio|\-|Path Style|✅|
|华为云|文档未说明是否兼容，工单反馈不保证兼容性，实际测试可以使用|Virtual Hosted Style|❓|
|Ucloud|只支持 8MB 大小的分片，本插件暂不支持<br><https://docs.ucloud.cn/ufile/s3/s3_introduction>|\-|❌|

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
