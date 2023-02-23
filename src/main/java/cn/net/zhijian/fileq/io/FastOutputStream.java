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

import java.io.BufferedOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import cn.net.zhijian.fileq.intf.IOutputStream;

/**
 * Write files base on BufferedOutputStream,
 * It's very fast, but it's not very good because of the writing latency.
 * @author liguoyong77@sina.com
 *
 */
public class FastOutputStream implements IOutputStream {
    private int size = 0;
    private FileOutputStream fos;
    private BufferedOutputStream bos;
    public final String name;

    public FastOutputStream(String file) throws FileNotFoundException {
        fos = new FileOutputStream(file);
        bos = new BufferedOutputStream(fos);
        name = file;
    }

    @Override
    public void write(byte[] content, int offset, int len) throws IOException {
        bos.write(content, offset, len);
        size += len;
    }

    @Override
    public void write(byte[] content) throws IOException {
        bos.write(content);
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
        if(bos != null) {
            bos.close();
            bos = null;
        }

        if(fos != null) {
            fos.close();
            fos = null;
        }
    }

    @Override
    public void flush() throws IOException {
        bos.flush();
    }
}
