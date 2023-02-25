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
package cn.net.zhijian.fileq;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IDispatcher;
import cn.net.zhijian.fileq.intf.IMessage;
import cn.net.zhijian.fileq.intf.IMessageHandler;
import cn.net.zhijian.fileq.intf.IReader;
import cn.net.zhijian.fileq.util.LogUtil;

/**
 * Receive writer's notification, read a message,
 * then send it to consumers' thread pool.
 * There is no more processing in it, only distribute.
 * So,in Dispatcher,one thread handles all queues' read-action.
 * 
 * @author Lgy
 *
 */
class Dispatcher extends Thread implements IDispatcher {
    private static final Logger LOG = LogUtil.getInstance();
    private static final int WAIT_TIME = 1 * 1000;
    
    private final ExecutorService threadPool;
	//auto confirmed, needn't call reader.confirm in message handler.
	//Set it to false when message handler is asynchronous.
    private final boolean autoConfirm;
    private final Map<String, Queue> queues = new ConcurrentHashMap<>();

    private long totalMsgNum = 0L;
    private boolean goon = true; //continue to run or not
    /* 
     * Can't use ReentrantLock, because lock and unlock of ReentrantLock
     * must be called in the same thread. And if lock twice
     * in the same thread, it will return right now.
     */
    private Object lock = new Object();
    private volatile boolean tracing = true; 
    
    private class Consumer implements Closeable {
        private final IReader reader;
        private final IMessageHandler handler;
        private final String queueName;
        private final String name;
        
        public Consumer(IReader reader, IMessageHandler handler) {
            this.reader = reader;
            this.handler = handler;
            this.queueName = reader.queueName();
            this.name = reader.name();
        }

        public String name() {
            return name;
        }
        
        public int curFileNo() {
            return reader.curFileNo();
        }
        
        public void close() {
            try {
                this.reader.close();
            } catch(Exception e) {
                LOG.error("Fail to close consumer reader {}.{}", queueName, name, e);
            }
        }
        
        public void hasten() {
            this.reader.hasten();
        }
        
        public void handle(IMessage msg) {
            try {
                boolean result = handler.handle(msg, reader);
                if(autoConfirm) {
                    reader.confirm(result);
                }
            } catch(Exception e) { //catch all exceptions to avoid thread crashes
                LOG.error("Fail to handle msg from queue({}) in {}", name, queueName, e);
            }
        }
        
        public IMessage read() {
            try {
                return reader.read();
            } catch(Exception e) { //catch all exceptions to avoid thread crashes
                LOG.error("Read msg from queue({}) in {}", name, queueName, e);
            }
            return null;
        }
    }
    
    private class Queue {
        private Consumer[] consumers = new Consumer[] {};
        
        void add(Consumer c) {
            for(Consumer ci : this.consumers) {
                if(ci.name().equals(c.name())) {
                    LOG.warn("Fail to add consumer {}, already exists", c.name());
                    return;
                }
            }
            Consumer[] consumers = new Consumer[this.consumers.length + 1];
            System.arraycopy(this.consumers, 0, consumers, 0, this.consumers.length);
            consumers[this.consumers.length] = c;
            this.consumers = consumers;
        }
        
        void remove(String name) {
            if(this.consumers.length == 0) {
                return;
            }
            int n = 0;
            Consumer[] consumers = new Consumer[this.consumers.length - 1];

            for(Consumer c : this.consumers) {
                if(!c.name().equals(name)) {
                    if(n == consumers.length - 1) {
                        LOG.warn("Consumer({}) not exists", name);
                        return;
                    }
                    consumers[n++] = c;
                } else {
                    c.close();
                }
            }
            
            this.consumers = consumers;
        }
        
        void removeAll() {
            for(Consumer c : this.consumers) {
                c.close();
            }
            this.consumers = new Consumer[] {};
        }
    }
    
    public Dispatcher(ExecutorService threadPool, boolean autoConfirm) {
        this.threadPool = threadPool;
        this.autoConfirm = autoConfirm;
    }
    
    @Override
    public void run() {
        LOG.info("Dispatcher started");
        int msgNum;
        int count;
        Queue queue;

        while(goon) {
            msgNum = 0;
            for(Map.Entry<String, Queue> q : queues.entrySet()) {
                queue = q.getValue();
                count = 0;
                for(Consumer c : queue.consumers) {
                    IMessage msg = c.read();
                    if(msg == null) {
                        continue;
                    }
                    count++;
                    threadPool.submit(() -> c.handle(msg));
                }
                msgNum += count;
            }

            if(msgNum == 0) {
                /*
                 * In sequential mode, more than 75% of the time was wasted here.
                 * When a message is in processing, dispatcher is blocked here.
                 * After handled, lock is waked up.
                 * Locked, waked up, again and again.
                 */
                tracing = false;
                synchronized(lock) {
                    try {
                        lock.wait(WAIT_TIME);
                    } catch (InterruptedException e) {
                    }
                }
                tracing = true;
                t2 = System.nanoTime();
                
                for(Map.Entry<String, Queue> q : queues.entrySet()) {
                    queue = q.getValue();
                    for(Consumer c : queue.consumers) {
                        //flush buffered data to disk if in bufferedPush mode  
                        c.hasten();
                    }
                }
            } else {
                totalMsgNum += msgNum;
            }
        }
        
        queues.forEach((k, c) -> {
            LOG.debug("Close queue {}", k);
            c.removeAll();
        });
        queues.clear();
        LOG.info("Dispatcher finished");
    }
    
    @Override
    public void shutdown() {
        goon = false;
        synchronized(lock) {
            lock.notifyAll();
        }
    }

    @Override
    public void ready() {
        if(tracing) { //Needn't notify, notification is a high cost operation
            return;
        }

        synchronized(lock) {
            lock.notifyAll();
        }
    }

    @Override
    public int minFileNo(String queueName) {
        Queue queue = queues.get(queueName);
        if(queue == null) {
            LOG.info("Queue({}) not exists", queueName);
            return 0;
        }
        
        int minNo = Integer.MAX_VALUE;
        for(Consumer c : queue.consumers) {
            if(minNo > c.curFileNo()) {
                minNo = c.curFileNo();
            }
        }
        return minNo == Integer.MAX_VALUE ? 0 : minNo;
    }

    @Override
    public long handledMsgNum() {
        return totalMsgNum;
    }    
    
    private Queue addQueue(String queueName) {
        Queue queue = queues.get(queueName);
        if(queue == null) {
            queue = new Queue();
            queues.put(queueName, queue);
        }
        return queue;
    }
    
    @Override
    public void addConsumer(IReader reader, IMessageHandler handler) throws FQException {
        Queue queue = addQueue(reader.queueName());
        queue.add(new Consumer(reader, handler));        
    }
    
    @Override
    public void rmvConsumer(final String queueName, final String name) {
        Queue queue = queues.get(queueName);
        if(queue == null) {
            LOG.info("Queue({}) not exists", queueName);
            return;
        }
        
        queue.remove(name);        
    }
    
    @Override
    public void rmvConsumers(String queueName) {
        Queue queue = queues.get(queueName);
        if(queue == null) {
            LOG.info("Queue({}) not exists", queueName);
            return;
        }
        queue.removeAll();
    }
}
