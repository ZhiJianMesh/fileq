package cn.net.zhijian.fileq;

import java.util.Random;
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
public class SequentialQueueOverTest extends TestBase {
    private static final int MSG_NUM = 100000;
    private static final int WAIT_TIME = 3000;
    private static Logger LOG = LogUtil.getInstance();

    private static CountDownLatch lock = new CountDownLatch(1);
	private static long handleTime = System.currentTimeMillis();
	private static AtomicInteger pollMsgNum = new AtomicInteger(0);
	private static int pushMsgNum = 0;  
	private static int msgLen = 0;
	private static Random random = new Random();
    
    public static void main(String[] args) {
        LOG.debug("Start SequentialQueueOverTest");
        ExecutorService threadPool = Executors.newCachedThreadPool();
        long start;
        Timer checkOver = new Timer("Checking");

        Dispatcher dispatcher = new Dispatcher(threadPool);
        String dir = FileUtil.addPath(workDir, "queue1");
        FileQueue.Builder builder = new FileQueue.Builder(dir, "tt")
                .dispatcher(dispatcher)
                .maxFileNum(3) //set small num and size to make it easily be overed
                .maxFileSize(1 * 1024 * 1024)
                .bufferedPush(false)
                .bufferedPoll(false);
        dispatcher.start();
        
        try {
            FileQueue fq = builder.build();
            for(int i = 0; i < 2; i++) {
                String name = "consumer_" + i;
                fq.addConsumer(name, true, false, new MessageHandler());
            }
            
            byte[] s = "abcdefghijklmnopqrstuvwxyz1234567890".getBytes();
            msgLen = s.length + Integer.BYTES;
            byte[] content = new byte[msgLen];
            System.arraycopy(s, 0, content, Integer.BYTES, s.length);
            start = System.currentTimeMillis();
            
            //push messages in a standalone thread
            new Thread() {public void run() {
                for(int i = 0; i < MSG_NUM; i++) {
                    IFile.encodeInt(content, i, 0);
                    try {
                        if(random.nextBoolean()) {
                            Thread.sleep(0, 1000);
                        }
                        fq.push(content);
                    } catch (Exception e) {
                        LOG.error("Fail to push msg", e);
                    }
                    pushMsgNum++;
                }
                long end = System.currentTimeMillis();
                long interval = end > start ? end - start : 1;
                LOG.debug("Push num:{},speed:{}/s,interval:{}ms", pushMsgNum, (1000L * pushMsgNum) / interval, interval);
            }}.start();
            
            checkOver.schedule(new TimerTask() {
				@Override
				public void run() { //check if there is any message, if  lasting 3s no message, then exit
					if(System.currentTimeMillis() - handleTime > WAIT_TIME) {
						lock.countDown();
					}
				}
            }, 1000, 1000);
            fq.pauseConsumer(null);
            Thread.sleep(1000);
            fq.continueConsumer("consumer_0");
            Thread.sleep(5000);
            fq.continueConsumer("consumer_1");
            
            lock.await();
            long interval = handleTime > start ? handleTime - start : 1;
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
        private int preNo = -1;
        private int n = 0;
        
        @Override
        public boolean handle(IMessage msg, IReader reader) {
            handleTime = System.currentTimeMillis();
            if(msg.len() != msgLen) {
                LOG.error("Invalid message len {} in {}", msg.len(), reader.name());
            }
            int no = IFile.parseInt(msg.message(), 0);
            if(no < 0 || no >= MSG_NUM) {
                LOG.error("Invalid msg no {} in {}.{}", no, reader.name(), reader.curFileNo());
            } else if(preNo + 1 != no) {
                if(preNo >= 0 && preNo != MSG_NUM - 1) {
                    LOG.error("Invalid msg no {}, preNo:{} in {}.{}", no, preNo, reader.name(), reader.curFileNo());
                }
            }
            preNo = no;
            pollMsgNum.getAndIncrement();
            n++;
            if((n & 0xfff) == 0) {
                LOG.debug("[{}]msg num:{},cur no:{}", reader.name(), n, no);
            }
            reader.confirm(true);
            return true;
        } 
    }
}
