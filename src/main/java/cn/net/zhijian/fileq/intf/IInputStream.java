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
package cn.net.zhijian.fileq.intf;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public interface IInputStream extends IFile, Closeable {
    /**
     * Read content from the stream,
     * @param buff Buffer,should promise it has enough memory to save the message
     * @return real read length
     * @throws IOException io exception
     */
    int read(byte[] buff) throws IOException;
    
    int read(byte[] buff, int offset, int len) throws IOException;
    
    /**
     * Skip n bytes
     * @param n The number of bytes to skip
     * @return The real length skipped
     * @throws IOException io exception
     */
    default long skip(int n) throws IOException {
        if(n <= 0) {
            return 0;
        }
        int bufSize = 1024;
        byte[] buf = new byte[bufSize];
        int readLen = bufSize;
        int len;
        int count = 0;
        for(; count < n && readLen > 0; count += readLen) {
            len = Math.min(n - count, bufSize);
            readLen = read(buf, 0, len);
        } 
        //BufferedInputStream.skip can't handle correctly,
        //return value is not right
        //long p = super.skip(n);
        return count;
    }

    /**
     * Get the reading position
     * @return position
     */
    int readPos();
    
    /**
     * @return Whether the stream has more content or not
     */
    boolean hasMore(int len);
    
    File file();
}
