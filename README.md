# fileq

#### 介绍
用java实现一个高效的本地文件队列，支持严格的顺序消费，同时也支持不关注顺序的消费。

#### 使用说明
推荐的使用方式见以下代码：

```
private static final int N = 10;
private static final Logger LOG = LogUtil.getInstance();

public static void main(String[] args) throws Exception {
	CountDownLatch counter = new CountDownLatch(N);
	if(!FQTool.started()) {
		ExecutorService threadPool = Executors.newCachedThreadPool();
		FQTool.start(threadPool, true/*如果在IMessageHandler中confirm，此处传false*/);
	}
	String queueDir = FileUtil.addPath(System.getProperty("user.dir"), "test");
	FileQueue.Builder builder = new FileQueue.Builder(queueDir, "queue")
		.maxFileNum(50) //根据需要缓存的最大长度，确定文件个数，即使文件数超过这个数字，队列也不会删除未消费的文件
		.maxFileSize(16 * 1024 * 1024) //单个文件不宜过大，根据队列的规模确定单个文件的大小，避免频繁产生新文件
		.bufferedPush(false) //及时落盘，但是性能只有20万每秒，满足绝大部分场景的性能需求
		.bufferedPoll(true); //poll时尽量使用缓存文件
	FileQueue fq = FQTool.create(builder);
	
	fq.addConsumer("sequential", true, (msg, reader) -> { //保证消费的顺序
		LOG.debug("Msg1:{}", new String(msg.message(), 0, msg.len()));
		counter.countDown();
		return true;
	});
	
	fq.addConsumer("concurrent", false, (msg, reader) -> { //不保证消费的顺序
		LOG.debug("Msg2:{}", new String(msg.message(), 0, msg.len()));
		counter.countDown();
		return true;
	});
	
	for(int i = 0; i < 10; i++) {
		fq.push(("test" + i).getBytes());
	}
	counter.await();
	LOG.debug("over");
	System.exit(0);
}
```

其他测试代码请参考项目中的test目录。

#### 性能测试
以下测试在8个虚拟核的笔记本上测试，消息为10字节，push 40万消息入队列。

以下测试都是大致数据，只用于数量级上的比较。

1）一个队列，2个并发消费者，采用缓存文件方式写入，缓存文件方式读出，

push约465万/秒， poll约50万/秒：

Push num:400000,speed:4651162/s

Poll num:800000,speed:506649/s

2）一个队列，16个并发顺序消费者，采用缓存文件写入，缓存方式读出，8个线程处理消息。

push约588万/秒，poll约48万/秒：

Push num:400000,speed:5882352/s

Poll num:6400000,speed:485105/s

因为每个消息需要消费线程确认，确认后才会读取下一个，分发线程大量时间用于睡眠、等待确认、唤醒，处于lock的时间超过75%。


3）混合模式，四个队列，两个队列用channel方式写队列，两个队列使用缓冲文件方式，消费队列都用缓存文件方式；每个队列两个消费者，一个并发消费，一个顺序消费。

push约39万/秒，poll约15万/秒：

Push num:1600000,speed:389768/s

Poll num:3200000,speed:155915/s


【总结】

1）push不需要考虑消息确认，也不用记录位置信息，所以性能极高，缓存文件模式下，轻松达到几百万，channel模式也可以超过20万；

2）poll至少需要读两次io，且需要尽量及时地记录消费位置，此操作占用将近1/3的时间，并发消费时，最高也只能达到150万，顺序消费时，16个消费者也只能达到48万。

3）顺序消费时，队列、消费者越多，性能越高，因为瓶颈不在IO，而是在等待消息处理结果上；如果有多个队列，或一个队列有多个消费者，消息分发线程就容易读到消息，处于lock状态机会就少。

以上描述，只是基于性能测试的角度来看的，真实的单机系统，能将本地文件队列压满的场景极少，也不一定可以将队列细分成多个，一个队列对应很多个消费者的场景也不多。

#### 软件架构
Provider -> Consumers

Provider将消息存入本地文件，如果超过文件最大容许的大小，则重新创建一个文件；如果文件数量超过上限，就会删除所有Consumers都已经消费过的文件。

每个Consumer独立消费，消费位置记录在独立的本地文件中。

本地文件在fileq中至关重要，所以，它所使用的路径不可随意删改。



#### 安装教程

本软件只依赖slf4j与logback。

引入源码或引入jar即可在项目中引用。


#### 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request