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

/**
 * FileQueue tool class
 * @author Lgy
 *
 */
public class FQTool {
    private static Dispatcher dispatcher = null;
    private static final List<FileQueue> queues = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * 
     * @param threadPool thread pool to execute message handler
     * @param autoConfirm if true,dispatcher will call reader.confirm, otherwise
     *   reader.confirm should be called in message handler. 
     *   It's useful in asynchronized handler
     */
    public static void start(ExecutorService threadPool, boolean autoConfirm) {
        if(!started()) {
            dispatcher = new Dispatcher(threadPool, autoConfirm);
            dispatcher.start();
        }
    }
    
    public static boolean started() {
        return dispatcher != null;
    }
    
    /**
     * Create a file queue
     * @param builder builder
     * @return FileQueue
     * @throws FQException
     */
    public static FileQueue create(FileQueue.Builder builder) throws FQException {
        if(!started()) {
            throw new FQException("FQTool not started");
        }
        
        FileQueue fq = get(builder.name);
        if(fq != null) {
            throw new FQException("FileQueue `" + builder.name + "` exists");
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
                break;
            }
            idx++;
        }
        
        if(idx >= 0) {
            queues.remove(idx);
        }
    }
    
    /**
     * Stop all file queues, dispatcher will be shutdown
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
