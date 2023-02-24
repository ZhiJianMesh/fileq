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
import cn.net.zhijian.fileq.util.FileUtil;
import cn.net.zhijian.fileq.util.LogUtil;
/**
 * 
 * @author Lgy
 * Mixed concurrent/sequential, buffered/channel
 * Write num:1600000, speed: 407747, interval:3924ms
 * Consume num:3998220, speed: 103621, interval:38585ms, handled message num:3998220
 * 
 */
public class MixedQueueTest extends TestBase {
    private static final int MSG_NUM = 400000;
    private static final int WAIT_TIME = 3000;
    private static Logger LOG = LogUtil.getInstance();

    private static CountDownLatch lock = new CountDownLatch(1);
	private static long lastHandleTime = System.currentTimeMillis();
    private static long firstHandleTime = -1;
	private static AtomicInteger handledMsgNum = new AtomicInteger(0);
    
    public static void main(String[] args) {
        int threadNum = Runtime.getRuntime().availableProcessors() * 2;
        LOG.debug("Start test, thread num {}", threadNum);
        ExecutorService threadPool = Executors.newFixedThreadPool(threadNum);
        long start;
        long end;
        Timer checkOver = new Timer("Checking");
        int pushMsgNum = 0;
        FileQueue[] fqs = new FileQueue[4];
        
        Dispatcher dispatcher = new Dispatcher(threadPool);
        dispatcher.start();
        try {
            fqs[0] = createQueue("q1", "t1", false, true, dispatcher);
            fqs[1] = createQueue("q1", "t2", false, true, dispatcher);
            fqs[2] = createQueue("q2", "t1", true, true, dispatcher);
            fqs[3] = createQueue("q2", "t2", true, true, dispatcher);
            
            byte[] content = new byte[10];
            byte[] s = "aaaaaa".getBytes();
            System.arraycopy(s, 0, content, Integer.BYTES, s.length);
            
            start = System.currentTimeMillis();
            for(int i = 0; i < MSG_NUM; i++) {
                IFile.encodeInt(content, i, 0);
                for(FileQueue fq : fqs) {
                    fq.push(content);
                    pushMsgNum++;
                }
            }
            end = System.currentTimeMillis();
            long interval = end > start ? end - start : 1;
            LOG.debug("Write num:{},speed:{}/s, interval:{}ms", pushMsgNum, (1000L * pushMsgNum) / interval, interval);
            
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
            LOG.debug("Read num:{},speed:{}/s, interval:{}ms, handled message:{}",
            		dispatcher.handledMsgNum(),
            		(1000L * handledMsgNum.get()) / interval,
            		interval,
            		handledMsgNum.get());
            dispatcher.shutdown();
        } catch (Exception e) {
            LOG.error("Failed", e);
        }
        checkOver.cancel();
        threadPool.shutdown();
        System.exit(0);
    }
    
    private static FileQueue createQueue(String queue, String topic,
            boolean bufferedPush, boolean bufferedPoll,
            Dispatcher dispatcher) throws FQException {
        String dir = FileUtil.addPath(workDir, queue);
        FileQueue.Builder builder = new FileQueue.Builder(dir, topic)
            .dispatcher(dispatcher)
            .maxFileNum(40)
            .maxFileSize(16 * 1024 * 1024)
            .bufferedPush(bufferedPush)
            .bufferedPoll(bufferedPoll);
        
        FileQueue fq = builder.build();
        fq.addConsumer("concurrent", false, (msg) -> {
            recordConsumeTime();
            if(msg.len() != 10) {
                LOG.error("Invalid msg len {}", msg.len());
            }
            int no = IFile.parseInt(msg.message(), 0);
            if(no < 0 || no >= MSG_NUM) {
                LOG.error("Invalid msg no {}", no);
            }
            handledMsgNum.getAndIncrement();
            return true;
        });
        
        fq.addConsumer("sequencial", true, new IMessageHandler() {
            private int preNo = -1;
            
            @Override
            public boolean handle(IMessage msg) {
                recordConsumeTime();
                if(msg.len() != 10) {
                    LOG.error("Invalid message len {}", msg.len());
                }
                int no = IFile.parseInt(msg.message(), 0);
                if(no < 0 || no >= MSG_NUM) {
                    LOG.error("Invalid msg no {}", no);
                } else if(preNo + 1 != no) {
                    if(preNo >= 0 && preNo != MSG_NUM) {
                        LOG.error("Invalid msg no {}, preNo:{}", no, preNo);
                    }
                }
                preNo = no;
                handledMsgNum.getAndIncrement();
                return true;
            }
        });
        
        return fq;
    }
    
    private static void recordConsumeTime() {
        lastHandleTime = System.currentTimeMillis();
        if(firstHandleTime < 0) {
            firstHandleTime = lastHandleTime;
        }
    }
}
