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

/**
 * queue file
 * @author Lgy
 *
 */
public interface IFile extends Closeable {
    int DEFAULT_BUF_LEN = 1024;
    
    byte[] MAGIC = "QUEUE".getBytes();
    /**
     * Queue file header: "QUEUE" + ver(1byte) + fileNo(4bytes) + Msgs
     * Each message: Sign(1bit) + HashFlag(1bit) + Len(30bits) [+ HashCode(4byte)] + content
     */
    int FILE_HEAD_LEN = MAGIC.length + 1 + Integer.BYTES;
    
    int VER = 0x00;
    int MAX_MSG_SIZE = (1 << 20);
    int MIN_FILESIZE = (1 << 23);
    
    int MSG_HASH_FLAG = 0x40000000;
    int MSG_LEN_MASK = 0x3fffffff;
    
    static int hashCode(byte[] b, int offset, int len) {
        int h = 0;
        int end = len + offset;
        for (int i = offset; i < end; i++) {
            h = (h << 6) - h; //*=31
            h += (((int)b[i]) & 0xff);
        }
        return h;
    }
    
    static int hashCode(byte[] b) {
        return hashCode(b, 0, b.length);
    }
    
    /**
     * Encode a integer value into buff
     * @param buf buffer to save integer value
     * @param v value
     * @param pos offset
     */
    static void encodeInt(byte[] buf, int v, int pos) {
        for (int i = Integer.BYTES - 1; i >= 0; i--) {
            buf[pos + i] = (byte) (v & 0xff);
            v >>= 8;
        }
    }
    
    static int parseInt(byte[] buf, int pos) {
        int v = 0;
        for (int i = 0; i < Integer.BYTES; i++) {
            v <<= 8;
            v |= ((int)buf[pos + i]) & 0xff;
        }
        return v;
    }
}
