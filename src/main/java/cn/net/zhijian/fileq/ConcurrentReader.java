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

import org.slf4j.Logger;

import cn.net.zhijian.fileq.bean.ConcurrentMessage;
import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.intf.IInputStream;
import cn.net.zhijian.fileq.intf.IMessage;
import cn.net.zhijian.fileq.intf.IReader;
import cn.net.zhijian.fileq.intf.IWriter;
import cn.net.zhijian.fileq.io.ConsumeState;
import cn.net.zhijian.fileq.io.FastInputStream;
import cn.net.zhijian.fileq.io.SafeInputStream;
import cn.net.zhijian.fileq.util.FileUtil;
import cn.net.zhijian.fileq.util.LogUtil;

/**
 * Concurrent reader.
 * It will not confirm handle result.
 * When a message get out from the queue,
 * it will never roll back if consume failed.
 * If messages can be "handled concurrently", use it.
 * 
 * Queues' read actions are all operated in one thread.
 * 
 * @author Lgy
 */
class ConcurrentReader implements IReader {
    private static final Logger LOG = LogUtil.getInstance();
    
    private final String name;
    private final IWriter writer;
    private final byte[] intBuf = new byte[Integer.BYTES];
    private final boolean buffered;

    protected IInputStream qFile;
    protected ConsumeState consumeState;
    
    /**
     * @param name Consumer name
     * @param writer Queue writer
     * @param buffered Set reader with buffered mode
     * @param bufferedPos
     *  Save consume-position to disk after `bufferedPos` times updating
     * @param pos Initial position(CUR,HEAD,END)
     * @throws IOException
     */
    public ConcurrentReader(String name, IWriter writer,
            boolean buffered, int bufferedPos,
            InitPosition pos) throws IOException {
        this.name = name;
        this.writer = writer;
        this.buffered = buffered;
        String stateFile = FileUtil.addPath(writer.dir(), writer.name() + '_' + name);
        this.consumeState = new ConsumeState(stateFile, bufferedPos);
        init(pos);
    }
    
    private void init(InitPosition pos) throws IOException {
        int curFileNo;
        int readPos;
        
        if(pos == InitPosition.END) {
            curFileNo = this.writer.curFileNo();
            readPos = this.writer.size();
        } else if(pos == InitPosition.HEAD){
            curFileNo = this.writer.minFileNo();
            readPos = FILE_HEAD_LEN;
        } else {
            curFileNo = this.consumeState.fileNo();
            readPos = this.consumeState.readPos();
            
            if(curFileNo > writer.curFileNo()) { //file not exists
                LOG.warn("Messages lost, file {} not exists, big than fileNo {}", 
                        writer.queueFileName(curFileNo), writer.curFileNo());
                curFileNo = writer.curFileNo();
                readPos = FILE_HEAD_LEN;
            }
            
            if(curFileNo < writer.minFileNo()) {//removed file, skip it
                LOG.warn("Messages lost, file {} not exists, smaller than fileNo {}",
                        writer.queueFileName(curFileNo), writer.minFileNo());
                curFileNo = writer.minFileNo();
                readPos = FILE_HEAD_LEN;
            }
        }
       
        LOG.debug("open `{}`,readPos:{}", writer.queueFileName(curFileNo), readPos);
        qFile = open(curFileNo);
        if(readPos > FILE_HEAD_LEN) {
            //first time read, skip the content that has been read
            qFile.skip(readPos - FILE_HEAD_LEN);
        }
    }

    private IInputStream open(int fileNo) throws IOException {
        String fn = writer.queueFileName(fileNo);
        File f = new File(fn);
        if(!f.exists()) {
            LOG.info("File `{}` not exists", fn);
            return null;
        }
        
        int fileSize = (int)f.length();
        if(fileSize < FILE_HEAD_LEN) {
            throw new IOException("Invalid queue file " + fn +", too short");
        }
        
        IInputStream qFile;
        if(buffered) {
            qFile = new FastInputStream(fn);
        } else {
            qFile = new SafeInputStream(fn);
        }
        
        byte[] head = new byte[FILE_HEAD_LEN];
        qFile.read(head);
        
        //magic(5)|ver(1)|fileNo(4)
        int ver = 0xff & ((int)head[MAGIC.length]);
        int no = IFile.parseInt(head, MAGIC.length + 1);
        if (ver != VER || !IFile.byteArrayEquals(head, 0, MAGIC, 0, MAGIC.length) || no != fileNo) {
            FileUtil.closeQuietly(qFile);
            throw new IOException("Invalid queue file " + fn
                    + ",ver=" + ver + ",no=" + no
                    + ",magic=" + new String(head, 0, MAGIC.length));
        }
        this.consumeState.save(fileNo, qFile.readPos(), true);
        
        return qFile;
    }
    
    protected IInputStream openNext() {
        IInputStream f = null;
        int fileNo = this.consumeState.fileNo() + 1;
        int last = writer.curFileNo();

        do {
            try {
                if((f = open(fileNo)) != null
                   && f.hasMore()) { //if only file head,ignore it
                    return f;
                }
            } catch (IOException e) {
                LOG.error("Fail to open file {}", fileName(fileNo), e);
                FileUtil.closeQuietly(f);
            }
            fileNo++;
        } while(fileNo < last);
        
        return null;
    }
    
    @Override
    public IMessage read() { //run in a single thread
        int curFileNo = this.consumeState.fileNo();

        if(curFileNo == writer.curFileNo()) {//read the last file
            if(qFile == null) {
                return null; //wait new content
            }
            
            if(!qFile.hasMore()) {
                this.consumeState.save(qFile.readPos(), false);//write pos when idle
                return null; //the writing file, wait for new content
            }
        } else if(qFile == null || !qFile.hasMore()) {
            //old file, when the reaching end point, open next one
            FileUtil.closeQuietly(qFile);
            if((qFile = openNext()) == null) {
                return null;
            }
        }
        
        try {
            int len = readInt();
            boolean chkHash = (len & MSG_HASH_FLAG) != 0;

            len &= MSG_LEN_MASK;
            if(len > MAX_MSG_SIZE) {
                qFile.skip(len);
                this.consumeState.save(qFile.readPos(), false);//write pos when idle
                LOG.warn("Invalid message length({}) in file {}@{}", len, qFile.name(), qFile.readPos());
                return null;
            }

            byte[] content = getBuffer(len);
            if(chkHash) {
                int hashCode = readInt();
                qFile.read(content, 0, len);
                if(hashCode != IFile.hashCode(content, 0, len)) {
                    LOG.warn("Invalid hash value at {} in {}", qFile.readPos() - len - 4, curFileName());
                    return null;
                }
            } else {
                qFile.read(content, 0, len);
            }
            
            //record read position in confirm
            return generateMessage(len, content);
        } catch (IOException e) {
            LOG.error("Fail to read file {}", curFileName(), e);
        }
        
        return null;
    }
    
    /**
     * Read a integer value from file.
     * Because Executed in a single thread, so `intBuf` can be a member variable
     * @return An integer value
     * @throws IOException
     */
    private int readInt() throws IOException {
        if(qFile.read(intBuf) != Integer.BYTES) {
            throw new IOException("Fail to read a int from file " + this.consumeState);
        }
        return IFile.parseInt(intBuf, 0);
    }

	/**
     * Create a new buffer to save message,
     * if consumer in multi-threads, create a new buffer each time.
     * @param len length of the message to be read
     * @return buffer
     */
    protected byte[] getBuffer(int len) {
        return new byte[len];
    }

    /**
     * Generate a message with the content from file, and send it to handlers
     * @param content Buffer to receive the message
     * @return message
     */
    protected IMessage generateMessage(int len, byte[] content) {
        return new ConcurrentMessage(len, content);
    }

    @Override
    public void confirm(boolean ok) { //called in multi-threads
        if(ok && qFile != null) {
            this.consumeState.save(qFile.readPos(), false);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        if(qFile != null) {
            this.consumeState.save(qFile.readPos(), true);
            FileUtil.closeQuietly(qFile);
            qFile = null;
        }
        FileUtil.closeQuietly(consumeState);
        this.consumeState = null;
    }
    
    @Override
    public int curFileNo() {
        return this.consumeState.fileNo();
    }
    
    @Override
    public String name() {
        return name;
    }
    
    @Override
    public String queueName() {
        return writer.queueName();
    }
    
    public String curFileName() {
        return fileName(this.consumeState.fileNo());
    }
    
    public String fileName(int no) {
        return writer.queueFileName(no);
    }

    @Override
    public IWriter writer() {
        return writer;
    }
    
    public void hasten() {
        if(qFile == null || !qFile.hasMore()) {
            writer.hasten();
        }
    }
}
