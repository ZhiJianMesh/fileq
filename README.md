# fileq

#### 介绍
用java实现一个高效的本地文件队列。

以下测试在8个虚拟核的笔记本上测试，消息为10字节，push 40万消息入队列。

以下测试都是大致数据，只用于数量级上的比较。

1）一个队列，两个并发消费者，采用缓存文件方式写入，缓存文件方式读出，

push约444万/秒， poll约50万/秒：

Write num:400000,speed:4651162/s

Read num:800000,speed:506649/s

2）一个队列，两个并发消费者，采用缓存文件写入，channel方式读出。

push约588万/秒，poll约6万/秒：

Write num:400000,speed:5882352/s

Read num:800000,speed:65509/s

因为每个消息需要消费线程确认，确认后才会读取下一个，大量时间用于等待确认、唤醒分发线程上


3）混合模式，四个队列，两个队列用channel方式写队列，两个队列使用缓冲文件方式，消费队列都用缓存文件方式；每个队列两个消费者，一个并发消费，一个顺序消费。

push月39万/秒，poll约15万/秒：

Write num:1600000,speed:389768/s

Read num:3200000,speed:155915/s

#### 软件架构
Provider -> Consumers

Provider将消息存入本地文件，如果超过文件最大容许的大小，则重新创建一个文件；如果文件数量超过上限，就会删除所有Consumers都已经消费过的文件。

每个Consumer独立消费，消费位置记录在独立的本地文件中。

本地文件在fileq中至关重要，所以，它所使用的路径不可随意删改。



#### 安装教程

本软件只依赖slf4j与logback。

引入源码或打成jar即可在项目中引用。

#### 使用说明
推荐的使用方式见以下代码：

```
if(!FQTool.started()) {
    ExecutorService threadPool = Executors.newCachedThreadPool();
    FQTool.start(threadPool);
}
String queueDir = "dir for queue";
FileQueue.Builder builder = new FileQueue.Builder(queueDir, "queue_name")
				.maxFileNum(50)
                .maxFileSize(16 * 1024 * 1024)
                .bufferedPush(false)
                .bufferedPoll(true);
FileQueue fq = FQTool.create(builder);
fq.addConsumer("sequential_consumer", true, (msg) -> {...});
```

如果不在意消费的顺序性，添加消费者时将顺序性设为false。

```
fq.addConsumer("conncurrent_consumer", false, (msg) -> {...});
```

#### 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request