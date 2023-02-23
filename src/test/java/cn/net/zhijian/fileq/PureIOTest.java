package cn.net.zhijian.fileq;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.intf.IInputStream;
import cn.net.zhijian.fileq.intf.IOutputStream;
import cn.net.zhijian.fileq.io.FastInputStream;
import cn.net.zhijian.fileq.io.FastOutputStream;
import cn.net.zhijian.fileq.io.SafeInputStream;
import cn.net.zhijian.fileq.io.SafeOutputStream;
import cn.net.zhijian.fileq.util.LogUtil;
import cn.net.zhijian.util.FileUtil;
/**
 * SafeOutputStream write->useTime:16195,Speed:1975918/s
 * FastOutputStream write->useTime:146,Speed:219178082/s
 * SafeInputStream read->useTime:11004,Speed:2908033/s
 * FastInputStream read->useTime:162,Speed:197530864/s
 * @author Lgy
 *
 */
public class PureIOTest extends TestBase {
    private static final int N = 4000000;
    private static Logger LOG = LogUtil.getInstance();
    
    public static void main(String[] args) throws Exception {
        String dir = FileUtil.addPath(workDir, "test");
        File f = new File(dir);
        if (!f.exists()) {
            LOG.info("Make dirs {}", dir);
            if(!f.mkdirs()) {
                throw new FQException("Fail to create dir " + dir);
            }
        }
        String fn = FileUtil.addPath(dir, "test1.bin");
        try(SafeOutputStream out = new SafeOutputStream(fn)) {
            testOutput("SafeOutputStream", out);
        }
        
        try(FastOutputStream out = new FastOutputStream(fn)) {
            testOutput("FastOutputStream", out);
        }
        
        try(SafeInputStream in = new SafeInputStream(fn)) {
            testInput("SafeInputStream", in);
        }
        
        try(FastInputStream in = new FastInputStream(new File(fn))) {
            testInput("FastInputStream", in);
        }
        System.exit(0);
    }
    
    private static long testInput(String name, IInputStream in) throws IOException {
        int v;
        long total = 0;
        byte[] buf = new byte[8];
        long start = System.currentTimeMillis();

        for(int i = 0; i < N; i++) {
            in.read(buf);
            v = IFile.parseInt(buf, 4);
            if(i != v) {
                LOG.error("Invalid content");
            }
            total += buf.length;
        }
        long end = System.currentTimeMillis();
        long useTime = end - start;
        LOG.debug("{} read->useTime:{},Speed:{}/s", name, useTime, (1000L * total) / useTime);
        return total;
    }
    
    private static long testOutput(String name, IOutputStream out) throws IOException {
        long total = 0;
        byte[] buf = new byte[8];
        byte[] t = "Test".getBytes();
        System.arraycopy(t, 0, buf, 0, t.length);
        
        long start = System.currentTimeMillis();
        for(int i = 0; i < N; i++) {
            IFile.encodeInt(buf, i, 4);
            total += buf.length;
            out.write(buf);
        }
        long end = System.currentTimeMillis();
        long useTime = end - start;
        LOG.debug("{} write->useTime:{},Speed:{}/s", name, useTime, (1000L * total) / useTime);
        return total;
    }
}
