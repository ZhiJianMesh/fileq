# fileq

## 介绍
在至简网格的开发中用了很多sqlite本地数据库，sqlite是单例的，为了实现不同实例间的同步，解耦数据写入与数据同步，需要一个本地文件队列来实现，所以做了这个。
用java实现一个高效的本地文件队列，实现不同功能模块之间的解耦，比如数据异步同步。支持严格的顺序消费，也支持不关注顺序的并发消费。
为什么不用kafka这些现成且成熟的队列，因为我的服务器要运行在资源极其受限的环境中，比如一部安卓手机、一个树莓派中，kafka太大了。也有现成的本地文件队列，为什么不用，因为它们大多不维护了，并且并不完全覆盖我需要的功能。


## 实现原理
Provider -> Consumers

Provider将消息顺序地存入本地文件，多个消费者从文件中顺序读取，每消费一次都会刷新消费位置，并定期顺序存入记录消费位置的文件。本实现利用了文件顺序读写的高效性，实现高效的队列。

Provider写入时，如果超过文件最大容许的大小，则重新创建一个文件；
如果文件数量超过上限，就会删除最老的文件（所有Consumers都已经消费过的文件）。如果有多个消费者，其中有一个很慢，则，即使文件数超过上限了，文件仍然会保留。

每个Consumer独立消费，消费位置记录在独立的本地文件中。记录位置的文件每次写入2个int，分别记录文件编号、在该文件中的消费位置。采用顺序写入的方式，重新加载时使用最后一对记录。
当该文件体积达到阈值时，重置文件，从头写起，丢掉前面无用的信息。这样的实现是为了利用文件顺序写入性能高的特点。

本地文件在fileq中至关重要，所以，它所使用的目录不可删改，否则会破坏队列。


## 使用说明
使用样例见以下代码：

```Java
private static final int N = 10;
private static final Logger LOG = LogUtil.getInstance();

public static void main(String[] args) throws Exception {
    CountDownLatch counter = new CountDownLatch(N);
    if(!FQTool.started()) {
        ExecutorService threadPool = Executors.newCachedThreadPool();
        FQTool.start(threadPool);
    }
    String queueDir = FileUtil.addPath(System.getProperty("user.dir"), "test");
    FileQueue.Builder builder = new FileQueue.Builder(queueDir, "queue")
        //根据缓存消息的量，确定文件个数，即使文件数超过这个数字，队列也不会删除尚未消费的文件
        .maxFileNum(50) 
        //单个文件不宜过大，根据队列的规模确定单个文件的大小，避免频繁产生新文件
        .maxFileSize(16 * 1024 * 1024) 
        //true表示用缓存方式写文件，性能高，但是宕机时，可能有部分消息没有落盘
        //false表示用channel方式，内容能及时落盘，但是性能只有20万每秒，能满足绝大部分场景的性能需求
        .bufferedPush(false)
        //true表示用缓存方式读文件，在异常宕机时，读出的内容可能尚未消费完，如果此时读取位置已更新，重启后就会丢失部分消息
        //false表示使用channel方式读文件，宕机时，出现读出内容尚未消费的情况相对要少很多，但是读取速度降低一个数量级
        .bufferedPoll(true)
        //消费位置更新多少次才落盘，此值越大，宕机重启时，重复消费的次数可能越多
        //设置的太小，会导致频繁的写消费位置，性能下降很严重，默认为1024个读取写一次盘
        .bufferedPos(100); 
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

其他测试代码请参考项目test目录下的代码。

## 性能测试
以下测试在8个虚拟核+固态硬盘的笔记本上测试，消息长度为10字节，push 40万消息入队列。

以下测试都是大致数据，只用于数量级上的比较。准确的数据依赖于使用场景，结合使用场景尚未长稳测试数据才更有说服力，
通常情况下，瓶颈不在队列，而在于业务处理，并且主要瓶颈在消费的处理逻辑。

1. 一个队列，16个并发消费者，采用缓存文件方式写入，缓存文件方式读出；


```
push约279万/秒， poll约196万/秒：

Push num:400000,speed:2797202/s

Poll num:6400000,speed:1963190/s
```


2. 一个队列，8个并发消费者，采用channel方式写入，缓存文件方式读出；


```
push约18万/秒， poll约146万/秒：

Push num:400000,speed:182398/s

Poll num:3200000,speed:1461187/s
```


3. 一个队列，16个顺序消费者，采用缓存文件写入，缓存方式读出，8个线程处理消息；


```
push约381万/秒，poll约104万/秒：

Push num:400000,speed:3809523/s

Poll num:6400000,speed:1043194/s
```


因为每个消息需要消费线程确认后才会读取下一个，所以消费性能出现大幅度下滑。


4. 混合模式，四个队列，两个队列用channel方式写队列，两个队列使用缓冲文件方式，消费队列都用缓存文件方式；每个队列两个消费者，一个并发消费，一个顺序消费；

```
push约39万/秒，poll约24万/秒：

Push num:1600000,speed:394477/s

Poll num:3200000,speed:238984/s
```

---
## 总结
- push不需要考虑消息确认，也不用记录位置信息，所以性能极高，缓存文件写入模式下，轻松达到每秒几百万，channel模式也可以达到20万；

- poll至少需要两次io，且需要尽量及时地记录消费位置，此操作占用将近1/3的时间，并发消费时，最高约200万，顺序消费时，16个消费者也只能达到100万。

- 顺序消费时，队列、消费者越多，性能越高，因为瓶颈不在IO，而是在等待消息处理结果上；如果有多个队列，或一个队列有多个消费者，消息分发线程就容易读到消息，park的机会就少。

以上描述，只是基于性能测试的角度来看的，真实的系统，能将本地文件队列压满的场景极少，也不一定可以将队列细分成多个，一个队列对应很多个消费者的场景也不多。


## 待改进点

1. 等待耗时
分发线程取消息时，可能没有没有新消息，如果用Object.wait等待provider写入，分发线程大量时间用于睡眠、等待确认、唤醒，整个队列处于lock的时间超过75%，并且不稳定，有时会出现超过10秒的lock（设置的超时时间为1秒）。换成LockSupport后，线程park的时间占用大幅减少，但是仍然超过30%，暂时想不出更好的优化方法。

2. 消费位置记录不及时
每次消费，消费位置都会更新，如果每次都写入文件，会导致大量时间用于写文件，所以等一段时间才会将消费位置写入文件。在异常宕机的情况，重启时可能会导致部分消息重复消费。

## 安装教程

本只依赖slf4j与logback，引入源码或引入jar即可在项目中使用。


## 参与贡献

1.  Fork 本仓库
2.  新建 Feat_xxx 分支
3.  提交代码
4.  新建 Pull Request