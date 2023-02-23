package cn.net.zhijian.fileq;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
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
    private static final int MSG_NUM = 500000;
    private static final int WAIT_TIME = 3000;
    private static Logger LOG = LogUtil.getInstance();
    private static CountDownLatch lock = new CountDownLatch(1);
	private static long handleTime;
    
    public static void main(String[] args) {
        LOG.debug("Start test");
        ExecutorService threadPool = Executors.newFixedThreadPool(4);
        long start;
        long end;
        Timer checkOver = new Timer("Checking");
        Dispatcher dispatcher = new Dispatcher(threadPool);
        String dir = FileUtil.addPath(workDir, "queue");
        FileQueue.Builder builder = new FileQueue.Builder(dir, "tt")
                .dispatcher(dispatcher)
                .maxFileNum(100)
                .maxFileSize(8 * 1024 * 1024)
                .bufferedPush(true)
                .bufferedPoll(true);
        
        dispatcher.start();
        AtomicInteger handledMsgNum = new AtomicInteger(0);
        
        try {
            FileQueue fq = builder.build();
            fq.addConsumer("consumerA", false, (msg, reader) -> {
            	handleTime = System.currentTimeMillis();
            	if(msg.len() == 10) {
            		LOG.error("Invalid message len {}", msg.len());
            	}
                handledMsgNum.incrementAndGet();
                return true;
            });
            
            fq.addConsumer("consumerB", false, (msg, reader) -> {
            	handleTime = System.currentTimeMillis();
            	if(msg.len() == 10) {
            		LOG.error("Invalid message len {}", msg.len());
            	}
                handledMsgNum.incrementAndGet();
                return true;
            });
            
            start = System.currentTimeMillis();
            byte[] content = new byte[10];
            byte[] s = "aaaaaa".getBytes();
            System.arraycopy(s, 0, content, Integer.BYTES, s.length);
            
            handleTime = System.currentTimeMillis();
            for(int i = 0; i < MSG_NUM; i++) {
                try {
                    IFile.encodeInt(content, i, 0);
                    fq.push(content);
                } catch (FQException e) {
                }
            }
            end = System.currentTimeMillis();
            long interval = end > start ? end - start : 1;
            LOG.debug("Write num:{}, speed: {}, interval:{}ms", MSG_NUM, (1000L * MSG_NUM) / interval, interval);
            
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
            LOG.debug("Consume num:{}, speed: {}, interval:{}ms, handled message num:{}",
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

    public static boolean initLog(File cfgFile) {
        if(!cfgFile.exists()) {
            System.out.println(cfgFile.getAbsolutePath() + " not exists");
            return false;
        }
        
        if(!cfgFile.isFile()) {
            System.out.println(cfgFile.getAbsolutePath() + " is not a config file");
            return false;
        }
        try {
            LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();
            
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(lc);
            lc.reset();
            configurator.doConfigure(cfgFile);
            StatusPrinter.printInCaseOfErrorsOrWarnings(lc);
        } catch (JoranException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
}
