/*
Copyright 2023 zhijian.net.cn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package cn.net.zhijian.fileq;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IDispatcher;
import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.intf.IOutputStream;
import cn.net.zhijian.fileq.intf.IWriter;
import cn.net.zhijian.fileq.io.FastInputStream;
import cn.net.zhijian.fileq.io.FastOutputStream;
import cn.net.zhijian.fileq.io.SafeOutputStream;
import cn.net.zhijian.fileq.util.FileUtil;
import cn.net.zhijian.fileq.util.LogUtil;

/**
 * Write messages to files
 * @author Lgy
 *
 */
final class Writer implements IWriter {
    private static final Logger LOG = LogUtil.getInstance();
    
    private final String dir;
    private final String name;
    private final String queueName;
    private final int maxFileSize;
    private final int maxFileNum;
    private final IDispatcher dispatcher;

    private int curFileNo = 0;
    private int minFileNo = Integer.MAX_VALUE;
    private IOutputStream qFile;
    private byte[] msgBuf = new byte[DEFAULT_BUF_LEN];
    private boolean buffered;

    public Writer(String dir, String name, int maxFileSize, int maxFileNum,
            boolean buffered, IDispatcher dispatcher) throws FQException {
        if (maxFileSize < MIN_FILESIZE) {
            throw new FQException("maxFileSize too small");
        }
        
        if (dispatcher == null) {
            throw new FQException("dispatcher must be set");
        }
        
        this.maxFileNum = maxFileNum;
        this.maxFileSize = maxFileSize;
        this.dir = dir;
        this.buffered = buffered;
        File f = new File(dir);
        if (!f.exists()) {
            LOG.info("Make dirs {}", dir);
            if(!f.mkdirs()) {
                throw new FQException("Fail to create dir " + dir);
            }
        }
        this.name = name;
        this.queueName = FileUtil.addPath(this.dir, this.name);
        this.dispatcher = dispatcher;

        try {
            init();
        } catch (IOException e) {
            throw new FQException(e);
        }
    }

    private void init() throws IOException {
        File ff = new File(dir);
        //list all files which name likes name + '.' + num
        File[] files = ff.listFiles((d, n) -> n.matches(name + "\\.\\d+"));

        int fileNum = 0;
        
        /*
         * Trace all valid queue files
         * to find the minimum and maximum fileNo
         */        
        if(files != null && files.length > 0) {
            int ver;
            int no;
            byte[] head = new byte[FILE_HEAD_LEN];
            
            for (File f : files) {
                try (FastInputStream qis = new FastInputStream(f)) {
                    qis.read(head);
                    ver = 0xff & ((int)head[MAGIC.length]);
                    if (ver == VER && Arrays.equals(head, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
                        no = IFile.parseInt(head, MAGIC.length + 1);
                        if (this.minFileNo > no) {
                            this.minFileNo = no;
                        }
                        if (this.curFileNo < no) {
                            this.curFileNo = no;
                        }
                        fileNum++;
                    }
                } catch (Exception e) {
                    LOG.error("Fail to read {}", f, e);
                }
            }
        }

        if (fileNum > 0) {
            curFileNo++; // move to next one, no matter whether it is full or not
        } else {
            minFileNo = 0;
            curFileNo = 0;
        }
        qFile = open(curFileNo);
    }

    private IOutputStream open(int fileNo) throws IOException {
        IOutputStream qFile;
        if(this.buffered) {
            qFile = new FastOutputStream(queueFileName(fileNo));
        } else {
            qFile = new SafeOutputStream(queueFileName(fileNo));
        }
        byte[] content = new byte[FILE_HEAD_LEN];
        System.arraycopy(MAGIC, 0, content, 0, MAGIC.length);
        content[MAGIC.length] = (byte)VER;
        IFile.encodeInt(content, fileNo, MAGIC.length + 1);
        qFile.write(content);
        qFile.flush();

        return qFile;
    }
    
    private void removeFiles(int lastestFileNo) {
        int curNum = lastestFileNo - this.minFileNo + 1;
        if (curNum < this.maxFileNum) {
            return;
        }
        int uselessNum = dispatcher.minFileNo(queueName) - this.minFileNo;
        int rmvNum = Math.min(curNum - this.maxFileNum, uselessNum); //不可以删除尚未消费的队列文件
        if(rmvNum <= 0) {
            return;
        }
        LOG.info("File num more than {}, remove {} files", this.maxFileNum, rmvNum);
        for (int i = 0; i < rmvNum; i++) {
            String fn = queueFileName(this.minFileNo + i);
            File f = new File(fn);
            if (!f.exists()) {
                continue;
            }
            LOG.info("Remove file {}", fn);
            try {
                if(!f.delete()) {
                    LOG.error("Fail to delete file {}", fn);
                }
            } catch (Exception e) {
                LOG.error("Fail to delete file {}", fn, e);
            }
        }
        this.minFileNo += rmvNum;
    }

    private void openNext() throws IOException {
        this.curFileNo++;
        FileUtil.closeQuietly(qFile);
        qFile = null;
        removeFiles(this.curFileNo);
        qFile = open(this.curFileNo);
    }

    @Override
    public void write(byte[] msg, int offset, int len, boolean chkHash) throws FQException {
        if (len > MAX_MSG_SIZE) {
            throw new FQException("Msg too long,len:" + len);
        }

        int pos = 0;
        int hashCode = 0;
        int writeLen = Integer.BYTES + len;
        if(chkHash) { //concurrent safe
            hashCode = IFile.hashCode(msg, offset, len);
            writeLen += Integer.BYTES;
        }
        
        synchronized(this) {
            if(msgBuf.length < writeLen) {
                msgBuf = new byte[writeLen * 3 / 2];
            }

            if (chkHash) {
                IFile.encodeInt(msgBuf, len | MSG_HASH_FLAG, pos);
                pos += Integer.BYTES;
                IFile.encodeInt(msgBuf, hashCode, pos);
                pos += Integer.BYTES;
            } else {
                IFile.encodeInt(msgBuf, len, pos);
                pos += Integer.BYTES;
            }
            System.arraycopy(msg, offset, msgBuf, pos, len);
            
            try {
                qFile.write(msgBuf, 0, writeLen);
                if (qFile.size() >= maxFileSize) {
                    openNext();
                }
            } catch (Exception e) {
                throw new FQException(e);
            }
        }

        dispatcher.ready();
    }
    
    @Override
    public synchronized void close() throws IOException {
        if(qFile != null) {
            qFile.flush();
            LOG.debug("Writer close {},size:{}", qFile.name(), qFile.size());
            FileUtil.closeQuietly(qFile);
            qFile = null;
        }
    }

    @Override
    public int curFileNo() {
        return curFileNo;
    }

    @Override
    public int minFileNo() {
        return minFileNo;
    }

    @Override
    public String dir() {
        return dir;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String queueName() {
        return queueName;
    }

    public String curFileName() {
        return queueFileName(curFileNo);
    }
    
    @Override
    public String queueFileName(int fileNo) {
        return FileUtil.addPath(dir, name + '.' + fileNo);
    }

    @Override
    public int size() {
        return qFile.size();
    }

    @Override
    public void hasten() {
        if(qFile != null) {
            try {
                qFile.flush();
            } catch (IOException e) {
                LOG.error("Fail to flush buffered data to disk", e);
            }
        }
    }
}
