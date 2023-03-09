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

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import cn.net.zhijian.fileq.intf.IOutputStream;

/**
 * Save all messages into a file at once.
 * 1)BufferedOutputStream has delay when writing,
 *   If don't care about it, set bufferedPush mode to use it;
 * 2)FileChannel is fast, easy to use, and more compatible.
 * 3)MappedByteBuffer is the most efficient way.
 *   But it has latency to store data to disk,and it's very hard to close.
 *   In android, there are compatible problems.
 *   So give it up;
 * @author liguoyong77@sina.com
 *
 */
public final class SafeOutputStream implements IOutputStream {
    public final String name;
    private int size = 0;
    private FileOutputStream fos;
    private FileChannel fc;

    public SafeOutputStream(String file) throws FileNotFoundException {
        fos = new FileOutputStream(file);
        fc = fos.getChannel();
        name = file;
    }

    @Override
    public void write(byte[] content, int offset, int len) throws IOException {
        fc.write(ByteBuffer.wrap(content, offset, len));
        size += len;
    }
    
    @Override
    public void write(byte[] content) throws IOException {
        fc.write(ByteBuffer.wrap(content));
        size += content.length;
    }

    @Override
    public int size() {
        return size;
    }
    
    @Override
    public String name() {
        return name;
    }

    @Override
    public void close() throws IOException {
        if(fc != null) {
            fc.close();
            fc = null;
        }

        if(fos != null) {
            fos.close();
            fos = null;
        }
    }

    /**
     * Needn't flush,all are saved to disk right now
     */
    @Override
    public void flush() throws IOException {
    }
}
