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

import cn.net.zhijian.fileq.FQException;

/**
 * Queue messages' dispatcher
 * @author Lgy
 *
 */
public interface IDispatcher {
    /**
     * Called by writer when a new message coming
     */
    void ready();
    
    /**
     * Get the minimal file no of a queue
     * @param queueName
     * @return
     */
    int minFileNo(String queueName);
    
    /**
     * Get handled message number of a all queues 
     * @return
     */
    long handledMsgNum();
    
    void addConsumer(IReader reader, IMessageHandler handler) throws FQException;

    void rmvConsumer(final String queueName, final String name);
    
    void rmvConsumers(String queueName);
    
    /**
     * Stop the thread, and close all readers
     */
    void shutdown();
}
