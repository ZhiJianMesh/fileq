package cn.net.zhijian.fileq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.util.LogUtil;
import cn.net.zhijian.util.FileUtil;

public class FQToolTest {
    private static final int N = 10;
    private static final Logger LOG = LogUtil.getInstance();
    
    public static void main(String[] args) throws Exception {
        CountDownLatch counter = new CountDownLatch(N * 2);
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
}
