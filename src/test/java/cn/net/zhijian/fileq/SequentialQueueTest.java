package cn.net.zhijian.fileq;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IFile;
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
public class SequentialQueueTest extends TestBase {
    private static final int MSG_NUM = 400000;
    private static final int WAIT_TIME = 3000;
    private static Logger LOG = LogUtil.getInstance();

    private static CountDownLatch lock = new CountDownLatch(1);
	private static long handleTime = System.currentTimeMillis();
	private static AtomicInteger pollMsgNum = new AtomicInteger(0);
    
    public static void main(String[] args) {
        LOG.debug("Start test");
        int threadNum = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = Executors.newFixedThreadPool(threadNum);
        long start;
        long end;
        Timer checkOver = new Timer("Checking");
        int pushMsgNum = 0;        
        
        Dispatcher dispatcher = new Dispatcher(threadPool);
        String dir = FileUtil.addPath(workDir, "queue1");
        FileQueue.Builder builder = new FileQueue.Builder(dir, "tt")
                .dispatcher(dispatcher)
                .maxFileNum(40)
                .maxFileSize(8 * 1024 * 1024)
                .bufferedPush(true)
                .bufferedPoll(true);
        dispatcher.start();
        
        try {
            FileQueue fq = builder.build();
            for(int i = 0; i < threadNum * 2; i++) {
                String name = "consumer_" + i;
                fq.addConsumer(name, true, false, new MessageHandler(name));
            }
            
            byte[] content = new byte[10];
            byte[] s = "aaaaaa".getBytes();
            System.arraycopy(s, 0, content, Integer.BYTES, s.length);
            
            start = System.currentTimeMillis();
            for(int i = 0; i < MSG_NUM; i++) {
                IFile.encodeInt(content, i, 0);
                fq.push(content);
                pushMsgNum++;
            }
            end = System.currentTimeMillis();
            long interval = end > start ? end - start : 1;
            LOG.debug("Push num:{},speed:{}/s,interval:{}ms", pushMsgNum, (1000L * pushMsgNum) / interval, interval);
            
            checkOver.schedule(new TimerTask() {
				@Override
				public void run() { //每秒检查一次是否还有更多的消息，如果3秒没收到，则结束
					if(System.currentTimeMillis() - handleTime > WAIT_TIME) {
						lock.countDown();
					}
				}
            }, 1000, 1000);
            
            lock.await();
            interval = handleTime > start ? handleTime - start : 1;
            LOG.debug("Poll num:{},speed:{}/s,interval:{}ms, handled message num:{}",
            		dispatcher.handledMsgNum(),
            		(1000L * dispatcher.handledMsgNum()) / interval,
            		interval,
            		pollMsgNum.get());
            dispatcher.shutdown();
        } catch (Exception e) {
            LOG.error("Failed", e);
        }
        checkOver.cancel();
        threadPool.shutdown();
        System.exit(0);
    }
    
    private static class MessageHandler implements IMessageHandler {
        private final String queueName;
        private int preNo = -1;
        
        public MessageHandler(String queueName) {
            this.queueName = queueName;
        }
        
        @Override
        public boolean handle(IMessage msg, IReader reader) {
            handleTime = System.currentTimeMillis();
            if(msg.len() != 10) {
                LOG.error("Invalid message len {} in {}", msg.len(), queueName);
            }
            int no = IFile.parseInt(msg.message(), 0);
            if(no < 0 || no >= MSG_NUM) {
                LOG.error("Invalid msg no {} in {}", no, queueName);
            } else if(preNo + 1 != no) {
                if(preNo >= 0 && preNo != MSG_NUM - 1) {
                    LOG.error("Invalid msg no {}, preNo:{} in {}", no, preNo, queueName);
                }
            }
            preNo = no;
            pollMsgNum.getAndIncrement();
            reader.confirm(true);
            return true;
        } 
    }
}
