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
import java.io.RandomAccessFile;
import java.util.Arrays;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.util.LogUtil;

/**
 * Consumer state
 * @author Lgy
 *
 */
public class ConsumeState implements Closeable, IFile {
    private static final Logger LOG = LogUtil.getInstance();
    private static final int MAX_SAVE_INTERVAL = 1000;
    private static final int MAX_BUFFER_TIMES = 1000;

    private final String fileName;
    private RandomAccessFile stateFile;
    private long recordTime = System.currentTimeMillis(); //save file time
    private int fileNo = 0;
    private int readPos = FILE_HEAD_LEN;
    private int bufferTimes = 0;

    public ConsumeState(String fileName) throws IOException {
        this.fileName = fileName;
        File f = new File(fileName);

        if(f.exists()) { //if not exists, all start from 0
            byte[] magic = new byte[MAGIC.length];
            this.stateFile = new RandomAccessFile(new File(this.fileName), "rwd");
            this.stateFile.read(magic);
            int ver = stateFile.read();
            int fileNo = stateFile.readInt(); //fixed to 0
            if (ver == VER && Arrays.equals(magic, MAGIC) && fileNo == 0) {
                this.fileNo = stateFile.readInt();
                this.readPos = stateFile.readInt();
            } else {
                LOG.error("Invalid consumer state recorder file {}, re-create it", this.fileName);
                create(0, FILE_HEAD_LEN);
            }
        } else {
            create(0, FILE_HEAD_LEN);
        }
    }
    
    private void create(int fileNo, int readPos) throws IOException {
        this.fileNo = fileNo;
        this.readPos = readPos;

        LOG.info("Create read-state file {}", this.fileName);
        stateFile = new RandomAccessFile(new File(this.fileName), "rwd");
        stateFile.write(MAGIC);
        stateFile.write(VER);
        stateFile.writeInt(0);  //fileNo
        stateFile.writeInt(fileNo);  //curFileNo
        stateFile.writeInt(readPos);  //readPos
    }

    @Override
    public synchronized void close() throws IOException {
        if(stateFile == null) {
            return;
        }
        LOG.debug("Close state {}, fileNo:{},readPos:{}", fileName, toString());
        save(true);
        stateFile.close();
        stateFile = null;
    }

    public void save(int fileNo, int readPos, boolean force) {
        this.bufferTimes++;
        this.fileNo = fileNo;
        this.readPos = readPos;
        save(force || this.bufferTimes >= MAX_BUFFER_TIMES);
    }

    public void save(int readPos, boolean force) {
        this.bufferTimes++;
        this.readPos = readPos;
        save(force || this.bufferTimes >= MAX_BUFFER_TIMES);
    }

    public void save(boolean force) {
        long cur = System.currentTimeMillis();
        if(!force) {
            if(cur - recordTime < MAX_SAVE_INTERVAL) {
                return;
            }
        }
        recordTime = cur;
        bufferTimes = 0;

        synchronized(stateFile) {
            try {
                stateFile.seek(FILE_HEAD_LEN);
                stateFile.writeInt(fileNo);
                stateFile.writeInt(readPos);
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
