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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import cn.net.zhijian.fileq.intf.IInputStream;

/**
 * Read message from a channel.
 * 1)BufferedInputStream is very fast, but it's hard to control,
 *   if you want a high performance, set bufferdPoll mode to use it;
 * 2)FileChannel is fast and easy to use, and more compatible;
 * 3)MappedByteBuffer is the most efficient way.
 *   But it has latency to save data to disk,and it's very hard to close.
 *   In android, there are compatible problems.
 *   So give it up.
 * @author liguoyong77@sina.com
 *
 */
public final class SafeInputStream implements IInputStream {
    public final File file;
    private FileInputStream fis;
    private FileChannel fc;
    private int readPos = 0;
    private volatile long size = 0;

    public SafeInputStream(File file) throws IOException {
        this.fis = new FileInputStream(file);
        this.fc = fis.getChannel();
        this.file = file;
    }

    @Override
    public int read(byte[] buff) throws IOException {
        int l = fc.read(ByteBuffer.wrap(buff));
        readPos += l;
        return l;
    }
    
    @Override
    public int read(byte[] buff, int offset, int len) throws IOException {
        int l = fc.read(ByteBuffer.wrap(buff, offset, len));
        readPos += l;
        return l;
    }

    @Override
    public int readPos() {
        return readPos;
    }
    
    @Override
    public boolean hasMore(int len) {
        if(size - readPos >= len) {
            return true;
        }

        try {
            //size() is a IO operation,
            //Here,need not a precise value, so use a cached one
            size = fc.size();
            return size - readPos >= len;
            //return fis.available() > 0; //is writing
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        if(fc != null) {
            fc.close();
            fc = null;
        }

        if(fis != null) {
            fis.close();
            fis = null;
        }
    }
    
    @Override
    public File file() {
        return file;
    }

    @Override
    public String toString() {
        return "(" + file + ",pos " + readPos + ",size " + size + ')';
    }
}
