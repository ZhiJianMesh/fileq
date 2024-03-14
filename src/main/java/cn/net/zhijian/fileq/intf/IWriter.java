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
import cn.net.zhijian.fileq.FQException;

/**
 * Queue file writer
 * @author Lgy
 *
 */
public interface IWriter extends IFile, Closeable {
    int curFileNo();
    int minFileNo();
    int size();
    String dir(); //queue file dir
    String name(); //queue name
    String queueName(); //dir + queue-name
    String queueFileName(int fileNo);
    void write(byte[] msg, int offset, int len, boolean chkHash) throws FQException;
    
    /**
     * Hasten writer to flush data to stream
     * when dispatcher is idle a moment.
     */
    void hasten();
    boolean isClosed();
}
