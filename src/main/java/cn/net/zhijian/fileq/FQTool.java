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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.util.LogUtil;

/**
 * FileQueue tool class
 * @author Lgy
 *
 */
public final class FQTool {
    private static final Logger LOG = LogUtil.getInstance();
    
    private static Dispatcher dispatcher = null;
    private static final List<FileQueue> queues = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 
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
     * @throws FQException
     */
    public static FileQueue create(FileQueue.Builder builder) throws FQException {
        if(!started()) {
            throw new FQException("FQTool not started");
        }

        FileQueue fq = get(builder.queueName());
        if(fq != null) {
            throw new FQException("FileQueue `" + builder.queueName() + "` exists");
        }

        builder.dispatcher(dispatcher);
        fq = builder.build();
        queues.add(fq);
        
        return fq;
    }

    public static FileQueue get(String name) {
        for(FileQueue fq : queues) {
            if(fq.name.equals(name)) {
                return fq;
            }
        }
        return null;
    }

    public static void remove(String name) {
        int idx = -1;
        
        for(FileQueue fq : queues) {
            if(fq.name.equals(name)) {
			    try {
                    fq.close();
                } catch (IOException e) {
                    LOG.error("Fail to close queue {}", fq.name, e);
                }
                break;
            }

            idx++;
        }
        
        if(idx >= 0) {
            queues.remove(idx);
        }
    }

    /**
     * Stop all file queues, the dispatcher will be shutdown
     * @throws IOException
     */
    public static void stop() throws IOException {
        if(!started()) {
            return;
        }
        
        for(FileQueue fq : queues) {
            fq.close();
        }
        dispatcher.shutdown();
    }
}
