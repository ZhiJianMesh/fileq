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

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.util.LogUtil;

/**
 * FileQueue tool class
 * @author flyinmind of csdn.net
 *
 */
public final class FQTool {
    private static final Logger LOG = LogUtil.getInstance();
    
    //not very frequently used, so no strict synchronization
    private static volatile Dispatcher dispatcher = null;
    private static final Map<String, FileQueue> queues = new ConcurrentHashMap<>();
    
    /**
     * Start default dispatcher
     * All file queues use the same dispatcher
     * @param threadPool Thread pool to execute message handler
     */
    public static void start(ExecutorService threadPool) {
        if(!started()) {
            dispatcher = new Dispatcher(threadPool);
            dispatcher.start();
        }
    }
    
    public static boolean started() {
        return dispatcher != null;
    }
    
    /**
     * Create a file queue.
     * To split a queue into many small queues is recommended.
     * More queues more efficient when they are consumed.
     * @param builder Builder
     * @return FileQueue
     * @throws FQException wrong state of file queue
     */
    public static FileQueue create(FileQueue.Builder builder) throws FQException {
        if(!started()) {
            throw new FQException("FQTool not started");
        }

        FileQueue fq = get(builder.queueName());
        if(fq != null) {
            return fq;
        }

        LOG.debug("Create queue {}", builder.queueName());
        builder.dispatcher(dispatcher);
        fq = builder.build();
        queues.put(fq.name, fq);
        
        return fq;
    }

    public static FileQueue get(String name) {
        return queues.get(name);
    }

    public static void remove(String name) {
    	FileQueue fq = queues.get(name);
    	if(fq != null) {
            try {
                fq.close();
            } catch (IOException e) {
                LOG.error("Fail to close queue {}", fq.name, e);
            }
            queues.remove(name);
    	}
    }

    /**
     * Stop all file queues, the dispatcher will be shutdown
     * @throws IOException file io exception
     */
    public static void stop() throws IOException {
        if(!started()) {
            return;
        }
        Collection<FileQueue> ff = queues.values();
        queues.clear(); //clear first then close queues one by one
        dispatcher.shutdown();

        for(FileQueue fq : ff) {
            fq.close();
        }
        dispatcher = null;
    }
}
