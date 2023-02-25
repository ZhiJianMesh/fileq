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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import cn.net.zhijian.fileq.intf.IInputStream;

/**
 * Read message from queue-files use buffered input stream.
 * It can obviously improve the performance of reading.
 * @author liguoyong77@sina.com
 *
 */
public class FastInputStream implements IInputStream {
    private static final int BUF_SIZE = 1024 * 1024;
    public final String name;

    private FileInputStream fis;
    private BufferedInputStream bis;
    private int readPos = 0;
    private int available = 0;

    public FastInputStream(String file) throws IOException {
        fis = new FileInputStream(file);
        bis = new BufferedInputStream(fis, BUF_SIZE);
        name = file;
    }
    
    public FastInputStream(File file) throws IOException {
        fis = new FileInputStream(file);
        bis = new BufferedInputStream(fis);
        name = file.getCanonicalPath();
    }

    @Override
    public int read(byte[] buff) throws IOException {
        int l = bis.read(buff);
        readPos += l;
        available -= l;
        return l;
    }
    
    @Override
    public int read(byte[] buff, int offset, int len) throws IOException {
        int l = bis.read(buff, offset, len);
        readPos += l;
        available -= l;
        return l;
    }

    @Override
    public int readPos() {
        return readPos;
    }
    
    @Override
    public boolean hasMore() {
        if(available > 0) {
            return true;
        }

        try {
            //available() is a high-cost IO operation,
            //Here,needn't a precise value, so use a cached one
            available = bis.available();
            return available > 0;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public void close() throws IOException {
        if(bis != null) {
            bis.close();
            bis = null;
        }

        if(fis != null) {
            fis.close();
            fis = null;
        }
    }

    @Override
    public String name() {
        return name;
    }
}
