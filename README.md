# fileq

#### 介绍
使用java，实现一个高效的本地文件队列。

#### 软件架构
Provider -> Consumers

Provider将消息存入本地文件，如果超过文件最大容许的大小，则重新创建一个文件；如果文件数量超过上限，就会删除所有Consumers都已经消费过的文件。

每个Consumer独立消费，消费位置记录在独立的本地文件中。

本地文件在实现中至为关键，所以，队列所使用的路径不可随意删改。



#### 安装教程

本软件只依赖slf4j与logback。
引入源码或打成jar即可在项目中引用。

#### 使用说明


```
if(!FQTool.started()) {
    ExecutorService threadPool = Executors.newCachedThreadPool();
    FQTool.start(threadPool);
}
String queueDir = "dir for queue";
FileQueue.Builder builder = new FileQueue.Builder(queueDir, "queue_name")
        .maxFileNum(50).maxFileSize(16 * 1024 * 1024);
FileQueue fq = FQTool.create(builder);
fq.addConsumer("consumer_name", true, new IMessageHandler() {...});
```


#### 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request

