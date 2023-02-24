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

/**
 * All read actions are handled in one thread.
 * In Dispatcher, only one thread, read all queues' files,
 * then distribute them to a thread pool to handle.
 * @author Lgy
 *
 */
public interface IReader extends IFile {
    /**
     * Current reading file no
     * It is used to judge which file should be reserved.
     * @return
     */
    int curFileNo();
    String name();
    String queueName();
    
    /**
     * Read message from queue files one by one
     * until there is no message left, then return null.
     * Should be called in a single thread.
     * @return
     */
    IMessage read();
    
    /**
     * Confirm whether the message is handled ok or not
     * @param result
     */
    void confirm(boolean result);
    
    /**
     * Message writer
     * @return
     */
    IWriter writer();
    
    void hasten();
}