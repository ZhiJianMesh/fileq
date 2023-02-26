package cn.net.zhijian.fileq;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.util.FileUtil;
import cn.net.zhijian.fileq.util.LogUtil;
/**
 * 
 * @author Lgy
 * 1)Directly use FileInputStream/FileOutputStream
 *   Write num:200000, speed: 67865, interval:2947
 *   Consume num:200000, speed: 51255, interval:3902, handled message num:200000
 * 
 * 2)Use FileChannel to read/write
 *   Write num:200000, speed: 191204, interval:1046
 *   Consume num:400000, speed: 154380, interval:2591, handled message num:400000
 * 
 * 3)Use bufferd input/output stream
 *   Write num:500000, speed: 4672897, interval:107ms
 *   Consume num:1000000, speed: 135851, interval:7361ms, handled message num:1000000
 */
public class ConcurrentQueueTest extends TestBase {
    private static final int MSG_NUM = 400000;
    private static final int WAIT_TIME = 3000;
    private static Logger LOG = LogUtil.getInstance();
    private static CountDownLatch lock = new CountDownLatch(1);
	private static long lastHandleTime;
    private static long firstHandleTime = -1;
    
    public static void main(String[] args) {
        LOG.debug("Start test");
        int threadNum = Runtime.getRuntime().availableProcessors();
        ExecutorService threadPool = Executors.newFixedThreadPool(threadNum);
        long start;
        long end;
        Timer checkOver = new Timer("Checking");
        Dispatcher dispatcher = new Dispatcher(threadPool, true);
        String dir = FileUtil.addPath(workDir, "queue");
        FileQueue.Builder builder = new FileQueue.Builder(dir, "tt")
                .dispatcher(dispatcher)
                .maxFileNum(100)
                .maxFileSize(8 * 1024 * 1024)
                .bufferedPush(false)
                .bufferedPoll(true);
        
        dispatcher.start();
        AtomicInteger handledMsgNum = new AtomicInteger(0);
        
        try {
            FileQueue fq = builder.build();
            for(int i = 0; i < threadNum; i++) {
                String name = "consumer_" + i;
                fq.addConsumer(name, false, (msg, reader) -> {
                    recordConsumeTime();
                	if(msg.len() != 10) {
                		LOG.error("Invalid message len {} in {}", msg.len(), name);
                	}
                    handledMsgNum.incrementAndGet();
                    return true;
                });
            }
            
            byte[] content = new byte[10];
            byte[] s = "aaaaaa".getBytes();
            System.arraycopy(s, 0, content, Integer.BYTES, s.length);
            
            start = System.currentTimeMillis();
            lastHandleTime = System.currentTimeMillis();
            for(int i = 0; i < MSG_NUM; i++) {
                try {
                    IFile.encodeInt(content, i, 0);
                    fq.push(content);
                } catch (FQException e) {
                }
            }
            end = System.currentTimeMillis();
            long interval = end > start ? end - start : 1;
            LOG.debug("Push num:{},speed:{}/s, interval:{}ms", MSG_NUM, (1000L * MSG_NUM) / interval, interval);
            
            checkOver.schedule(new TimerTask() {
				@Override
				public void run() { //每秒检查一次是否还有更多的消息，如果3秒没收到，则结束
					if(System.currentTimeMillis() - lastHandleTime > WAIT_TIME) {
						lock.countDown();
					}
				}
            }, 1000, 1000);
            
            lock.await();
            interval = lastHandleTime > firstHandleTime ? lastHandleTime - firstHandleTime : 1;
            LOG.debug("Poll num:{},speed:{}/s,interval:{}ms, handled message num:{}",
            		dispatcher.handledMsgNum(),
            		(1000L * handledMsgNum.get()) / interval,
            		interval,
            		handledMsgNum.get());
            dispatcher.shutdown();
            fq.close();
        } catch (Exception e) {
            LOG.error("Failed", e);
        }
        checkOver.cancel();
        threadPool.shutdown();
        System.exit(0);
    }
    
    private static void recordConsumeTime() {
        lastHandleTime = System.currentTimeMillis();
        if(firstHandleTime < 0) {
            firstHandleTime = lastHandleTime;
        }
    }
}
