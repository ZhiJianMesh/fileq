package cn.net.zhijian.fileq;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IMessage;
import cn.net.zhijian.fileq.intf.IMessageHandler;
import cn.net.zhijian.fileq.intf.IReader;
import cn.net.zhijian.fileq.util.FileUtil;
import cn.net.zhijian.fileq.util.LogUtil;
/**
 * 
 * @author Lgy
 * Use FileChannel to read/write
 * Write num:20000, speed:116959, interval:171
 * Consume num:40000, speed:50825, interval:787, handled message num:40000
 */
public class SequentialQueueRereadTest extends TestBase {
    private static final int MSG_NUM = 100;
    private static final int CONSUMER_NUM = 1;
    private static Logger LOG = LogUtil.getInstance();

    private static CountDownLatch lock = new CountDownLatch(CONSUMER_NUM);
	private static AtomicInteger pollMsgNum = new AtomicInteger(0);
    
    public static void main(String[] args) {
        LOG.debug("Start SequentialQueueOverTest");
        ExecutorService threadPool = Executors.newCachedThreadPool();

        Dispatcher worker = new Dispatcher(threadPool);
        String dir = FileUtil.addPath(workDir, "queue1");
        FileQueue.Builder builder = new FileQueue.Builder(dir, "tt")
                .dispatcher(worker)
                .maxFileNum(3) //set small num and size to make it easily be overed
                .maxFileSize(1 * 1024 * 1024)
                .bufferedPush(true)
                .bufferedPoll(true);
        worker.start();
        
        try {
            FileQueue fq = builder.build();
            for(int i = 0; i < CONSUMER_NUM; i++) {
                String name = "consumer_" + i;
                fq.addConsumer(name, true, FileQueue.InitPosition.END, false, new MessageHandler());
            }
            
            //push messages in a standalone thread
            new Thread() {public void run() {
                long start = System.currentTimeMillis();
                for(int i = 0; i < MSG_NUM; i++) {
                    byte[] content = ("consumer_" + i).getBytes();
                    try {
                        fq.push(content);
                    } catch (Exception e) {
                        LOG.error("Fail to push msg", e);
                    }
                }
                long end = System.currentTimeMillis();
                long interval = end > start ? end - start : 1;
                LOG.debug("Push num:{},speed:{}/s,interval:{}ms", MSG_NUM, (1000L * MSG_NUM) / interval, interval);
            }}.start();

            long start = System.currentTimeMillis();
            lock.await();
            long interval = System.currentTimeMillis() - start;
            LOG.debug("Poll num:{},speed:{}/s,interval:{}ms, handled message num:{}",
            		worker.handledMsgNum(),
            		(1000L * worker.handledMsgNum()) / (interval == 0 ? 1 : interval),
            		interval,
            		pollMsgNum.get());
            worker.shutdown();
        } catch (Exception e) {
            LOG.error("Failed", e);
        }
        threadPool.shutdown();
        System.exit(0);
    }
    
    private static class MessageHandler implements IMessageHandler {
        private int errTimes = 0;
        private int num = 0;
        
        @Override
        public boolean handle(IMessage msg, IReader reader) {
            String txt = new String(msg.message(), 0, msg.len());
            if(errTimes < 10) {
                reader.confirm(false);
                if(!txt.equals("consumer_0")) {
                    LOG.error("redo wrong msg:{}", txt);
                }
                errTimes++;
            } else {
                if(!txt.equals("consumer_" + num)) {
                    LOG.error("confirm msg wrong:{}", txt);
                }
                num++;
                reader.confirm(true);
                if(num >= MSG_NUM) {
                    lock.countDown();
                }
                pollMsgNum.incrementAndGet();
            }
            return true;
        } 
    }
}
