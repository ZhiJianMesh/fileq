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
 * Queue messages' dispatcher
 * @author flyinmind of csdn.net
 *
 */
public interface IDispatcher {
    /**
     * Called by writer when a new message coming
     */
    void ready();
    
    /**
     * Get the minimal file no of the queue
     * @param queueName name of the file queue
     * @return the minimums file no
     */
    int minFileNo(String queueName);
    
    /**
     * Get handled message number of all queues 
     * @return message number that have already been handled
     */
    long handledMsgNum();
    
    /**
     * Add a consumer to the queue
     * @param autoConfirm Automatically confirm messages
     * @param reader Queue file reader
     * @param handler Message handler
     */
    void addConsumer(boolean autoConfirm, IReader reader, IMessageHandler handler);

    /**
     * Remove a consumer from the queue
     * @param queueName name of the queue
     * @param consumerName name of the consumer
     */
    void rmvConsumer(String queueName, String consumerName);
    
    void rmvConsumers(String queueName);
    
    /**
     * Stop the thread, and close all consumers
     */
    void shutdown();
    /**
     * pause all consumers
     */
    void pauseAll();
    /**
     * continue all consumers
     */
    void continueAll();
    
    //pause a consumer
    void pauseConsumer(String queue, String consumer);
    //unpause a consumer
    void continueConsumer(String queue, String consumer);
}
