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
package cn.net.zhijian.fileq.io;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.intf.IOutputStream;
import cn.net.zhijian.fileq.util.LogUtil;

/**
 * Consume state, record consume position.
 * It will save position info to disk every 1000 times,
 * or it exceeds 1000ms until fore saving disk.
 * The position is written sequential, the last one is the real one. 
 * It is a high cost operation to save small content to a file.
 * It occupies about 1/3 time when reading a message.
 * @author Lgy
 *
 */
public final class ConsumeState implements Closeable, IFile {
    private static final Logger LOG = LogUtil.getInstance();
    private static final int MAX_SAVE_INTERVAL = 1000;
    //max problem is that it may cause re-consume 1000 messages
    private static final int MAX_SIZE = 100 * 1024 * Integer.BYTES * 2 + FILE_HEAD_LEN;

    private final String fileName;
    private final int maxBufferedTimes;
    private IOutputStream stateFile;
    private long recordTime = System.currentTimeMillis(); //save file time
    private int fileNo = 0;
    private int readPos = FILE_HEAD_LEN;
    private int bufferedTimes = 0;
    private byte[] buf = new byte[Integer.BYTES * 2];
    private boolean changed = false;

    public ConsumeState(String fileName, int bufferedTimes) throws IOException {
        this.fileName = fileName;
        this.maxBufferedTimes = bufferedTimes;
        File f = new File(fileName);

        if(!f.exists()) { //if not exists, all start from 0
            init(0, FILE_HEAD_LEN);
            return;
        }
        
        int fileNo = 0;
        int readPos = FILE_HEAD_LEN;
        load : try(FastInputStream fis = new FastInputStream(fileName)) {
            byte[] head = new byte[FILE_HEAD_LEN];
            int readLen = fis.read(head);
            if(readLen < FILE_HEAD_LEN) {
                break load;//invalid state file
            }
            //MAGIC(5) + ver(1) + fileNo(4)
            int ver = ((int)head[MAGIC.length]) & 0xff;
            fileNo = IFile.parseInt(head, MAGIC.length + 1);
            if (ver != VER || fileNo != 0
                || !Arrays.equals(head, 0, MAGIC.length, MAGIC, 0, MAGIC.length)) {
                break load; //invalid state file
            }
            
            //continue reading until the last one
            while((readLen = fis.read(buf)) == buf.length) {
                fileNo = IFile.parseInt(buf, 0);
                readPos = IFile.parseInt(buf, Integer.BYTES);
            }
        }
        init(fileNo, readPos);
    }
    
    private void init(int fileNo, int readPos) throws IOException {
        this.fileNo = fileNo;
        this.readPos = readPos;

        LOG.info("Create read-state file {}", this.fileName);
        this.stateFile = new SafeOutputStream(this.fileName);
        //MAGIC(5) + ver(1) + 0(4) + fileNo(4) + readPos(4) ...
        byte[] head = new byte[FILE_HEAD_LEN + Integer.BYTES * 2];
        System.arraycopy(MAGIC, 0, head, 0, MAGIC.length);
        head[MAGIC.length] = VER;
        IFile.encodeInt(head, 0, MAGIC.length + 1);
        IFile.encodeInt(head, fileNo, FILE_HEAD_LEN);
        IFile.encodeInt(head, readPos, FILE_HEAD_LEN + Integer.BYTES);
        this.stateFile.write(head);
        this.stateFile.flush();
    }

    @Override
    public synchronized void close() throws IOException {
        if(stateFile == null) {
            return;
        }
        LOG.debug("Close state {}, {}", fileName, this);
        save(true);
        stateFile.close();
        stateFile = null;
    }

    public void save(int fileNo, int readPos, boolean force) {
        this.changed = this.changed || readPos != this.readPos || fileNo != this.fileNo;
        if(this.changed) {
            this.bufferedTimes++;
            this.fileNo = fileNo;
            this.readPos = readPos;
        }
        save(force || this.bufferedTimes >= maxBufferedTimes);
    }

    public void save(int readPos, boolean force) {
        this.changed = this.changed || readPos != this.readPos;
        if(this.changed) {
            this.bufferedTimes++;
            this.readPos = readPos;
        }
        save(force || this.bufferedTimes >= maxBufferedTimes);
    }

    public void save(boolean force) {
        long cur = System.currentTimeMillis();
        if(!force) {
            if(cur - recordTime < MAX_SAVE_INTERVAL || !this.changed) {
                return;
            }
        }
        recordTime = cur;
        bufferedTimes = 0;

        synchronized(stateFile) {
            try {
                if(stateFile.size() >= MAX_SIZE) { //if too large, rewrite it
                    stateFile.close();
                    init(fileNo, readPos);
                } else {
                    /*
                     * Merge 2 integer values into a buffer
                     * to reduce write-operation
                     */
                    IFile.encodeInt(buf, fileNo, 0);
                    IFile.encodeInt(buf, readPos, Integer.BYTES);
                    stateFile.write(buf);
                    stateFile.flush(); //It's very important, save it to disk right now
                }
            } catch (IOException e) {
                LOG.error("Fail to save consumer {} state", this.fileName, e);
            }
        }
    }

    public int fileNo() {
        return fileNo;
    }

    public int readPos() {
        return readPos;
    }
    
    @Override
    public String toString() {
        return "@(" + fileNo + ',' + readPos + ')';
    }
}
