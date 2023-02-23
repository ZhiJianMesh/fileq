package cn.net.zhijian.fileq;

import java.io.File;
import java.util.concurrent.CountDownLatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;
import cn.net.zhijian.fileq.util.FileUtil;
import cn.net.zhijian.fileq.util.LogUtil;

public class TestBase {
    protected static String workDir = System.getProperty("user.dir");
    private static Logger LOG;
    
    static {
        initLog(new File(FileUtil.addPath(workDir, "logback.xml")));
        LOG = LogUtil.getInstance();
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
    
    public static long performTest(String name, int threadNum, Runnable test) {
        long start = System.currentTimeMillis();
        CountDownLatch lock = new CountDownLatch(threadNum);
        for(int i = 0; i < threadNum; i++) {
            new Thread() {
                public void run() {
                    try {
                        test.run();
                        //LOG.info("test");
                    } catch(Exception e) {
                        LOG.error("Fail to run {}", name, e);
                    } finally {
                        lock.countDown();
                    }
                }
            }.start();
        }
        try {
            lock.await();
        } catch (InterruptedException e) {
        }
        long end = System.currentTimeMillis();
        LOG.info("{},time:{}", name, end - start);
        return end - start;        
    }
}
