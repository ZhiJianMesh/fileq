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
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;

import cn.net.zhijian.fileq.intf.IDispatcher;
import cn.net.zhijian.fileq.intf.IFile;
import cn.net.zhijian.fileq.intf.IMessageHandler;
import cn.net.zhijian.fileq.intf.IReader;
import cn.net.zhijian.fileq.intf.IWriter;
import cn.net.zhijian.fileq.util.LogUtil;
import cn.net.zhijian.fileq.util.FileUtil;

/**
 * main class
 * @author liguoyong77@sina.com
 *
 */
public final class FileQueue implements IFile {
    public static final int DEFAULT_QFILE_SIZE = 64 * 1024 * 1024;
    public static final int DEFAULT_QFILE_NUM = 16;

    private static final Logger LOG = LogUtil.getInstance();

    //messages dispatcher, multi queues can share one dispatcher
    private final IDispatcher dispatcher;

    //only one writer, more than one consumers
    private final IWriter writer;
    private final boolean bufferedPoll;
    private final int bufferedPos;
    public final String name;
    
    private FileQueue(Builder builder) throws FQException {
        if(builder.dispatcher == null) {
            throw new FQException("Dispatcher not set");
        }
        this.writer = new Writer(builder.dir, builder.name,
                builder.maxFileSize, builder.maxFileNum,
                builder.bufferedPush, builder.dispatcher);
        this.dispatcher = builder.dispatcher;
        this.name = builder.queueName();
        this.bufferedPoll = builder.bufferedPoll;
        this.bufferedPos = builder.bufferedPos;
        LOG.debug("Create queue `{}`", this.name);
    }

    /**
     * Write message to file queue, only one thread can write at the same time
     * @param msg Message should be written, FileQueue doesn't care the content
     * @param offset offset of the message buffer
     * @param len message length
     * @throws FQException write exception
     */
    public void push(byte[] msg, int offset, int len) throws FQException {
        this.writer.write(msg, offset, len, false);
    }

    public void push(byte[] msg) throws FQException {
        this.writer.write(msg, 0, msg.length, false);
    }

    /**
     * Write message to file queue,
     * in one queue only one thread can write at the same time
     * @param msg pushed message
     * @param offset offset of the msg
     * @param len lenght of the msg
     * @param chkHash If true, will check the message hash code
     * @throws FQException write exception
     */
    public void push(byte[] msg, int offset, int len, boolean chkHash) throws FQException {
        this.writer.write(msg, offset, len, chkHash);
    }

    public void push(byte[] msg, boolean chkHash) throws FQException {
        this.writer.write(msg, 0, msg.length, chkHash);
    }

    /**
     * Add a consumer to dispatcher
     * @param name consumer name
     * @param sequential
     *     If true, each message is handled one by one, until it's confirmed.
     *     If false, messages are handled concurrently, and doesn't care about result
     * @param cp Initital position
     * @param handler message handler
     * @param autoConfirm Automatically confirm messages
     *  If true,dispatcher will call reader.confirm automatically, otherwise
     *  reader.confirm should be called in message handler yourself. 
     *  It's useful in asynchronous/synchronous handler
     * @throws FQException wrap of IOException
     */
    public synchronized void addConsumer(String name, boolean sequential,
            InitPosition cp, boolean autoConfirm, IMessageHandler handler) throws FQException {
        if(writer.isClosed()) {
            throw new FQException("invalid writer,it's closed");
        }
        IReader reader;
        try {
            if(sequential) {
                reader = new SequentialReader(name, writer, dispatcher, bufferedPoll, bufferedPos, cp);
            } else {
                reader = new ConcurrentReader(name, writer, bufferedPoll, bufferedPos, cp);
            }
        } catch(IOException e) {
            throw new FQException(e);
        }
        dispatcher.addConsumer(autoConfirm, reader, handler);
    }
    
    public void addConsumer(String name, boolean sequential, boolean autoConfirm,
            IMessageHandler handler) throws FQException {
        addConsumer(name, sequential, InitPosition.CUR, autoConfirm, handler);
    }

    public void addConsumer(String name, boolean sequential,
            IMessageHandler handler) throws FQException {
        addConsumer(name, sequential, InitPosition.CUR, true, handler);
    }
    

    /**
     * pause a consumer, not stopped
     * @param consumer consumer name, if it's null, pause all
     */
    public void pauseConsumer(String consumer) {
        dispatcher.pauseConsumer(this.name, consumer);
    }

    /**
     * continue a consumer
     * @param consumer consumer name, if it's null, continue all
     */
    public void continueConsumer(String consumer) {
        dispatcher.continueConsumer(this.name, consumer);
    }
    
    /**
     * Remove a consumer
     * @param name Consumer name
     */
    public synchronized void rmvConsumer(String name) {
        dispatcher.rmvConsumer(writer.queueName(), name);
    }

    public synchronized void close() throws IOException {
        if(writer.isClosed()) {
            return;
        }
        LOG.info("Close the queue {}", writer.queueName());
        dispatcher.rmvConsumers(writer.queueName());
        writer.close();
    }
    
    public static class Builder {
        private final String dir;
        private final String name;
        private int maxFileSize = DEFAULT_QFILE_SIZE;
        private int maxFileNum = DEFAULT_QFILE_NUM;
        private boolean bufferedPush = false;
        private boolean bufferedPoll = false;
        //save position info into file after updating `bufferedPos` times
        private int bufferedPos = 1024;
        private IDispatcher dispatcher;
        
        /**
         * 
         * @param dir Queue dir
         * @param name Queue name.Files under the queue are named with 'name' + fileNo
         */
        public Builder(String dir, String name) {
            this.dir = dir;
            this.name = name;
        }
        
        /**
         * Set max queue file size.
         * Too large file is not a good idea, it must be smaller than 4G.
         * @param size File size
         * @return Builder
         */
        public Builder maxFileSize(int size) {
            this.maxFileSize = size;
            return this;
        }
        
        /**
         * Max number of files under a queue directory.
         * Too many files is not a good idea, it should be smaller than 500.
         * @param num num of files
         * @return Builder
         */
        public Builder maxFileNum(int num) {
            this.maxFileNum = num;
            return this;
        }
        
        /**
         * After each message polled out, it will record consume-position to a file.
         * It's high cost to write it directly to disk each time.
         * It occupies more than 1/3 time of the whole poll-processing.
         * So, buffer it into 2 integer variables, after bufferedPos times,
         * then, save them to disk.
         * It improves the performance, but it lead in a risk.
         * If the program crashed, re-start again, it will consume `bufferedPos`
         * messages repeatedly.
         * @param bufferedPos  write position info into file after 'bufferedPos' times
         * @return Builder
         */
        public Builder bufferedPos(int bufferedPos) {
            this.bufferedPos = bufferedPos;
            return this;
        }
        
        /**
         * Set buffered push mode, It can improve the performance about ten times.
         * But it is not a good idea, because the latency of writing disk.
         * @param bufferedPush Whether pushed content is buffered or written to disk right now.
         * @return Builder
         */
        public Builder bufferedPush(boolean bufferedPush) {
            this.bufferedPush = bufferedPush;
            return this;
        }
        
        /**
         * Set buffered poll mode.
         * It can improve the poll performance, but it's not obvious when too few queues.
         * @param buffered Whether pre-read into buffer enabled or not.
         * @return Builder
         */
        public Builder bufferedPoll(boolean buffered) {
            this.bufferedPoll = buffered;
            return this;
        }
        
        /**
         * Set messages dispatcher
         * @param dispatcher messages dispatcher
         * @return Builder
         */
        Builder dispatcher(IDispatcher dispatcher) {
            this.dispatcher = dispatcher;
            return this;
        }
        
        /**
         * Create a dispatcher
         * @param threadPool thread pool
         * @return Builder
         */
        Builder createDispatcher(ExecutorService threadPool) {
            this.dispatcher = new Dispatcher(threadPool);
            return this;
        }
        
        public FileQueue build() throws FQException {
            return new FileQueue(this);
        }
        
        public String queueName() {
            return FileUtil.addPath(dir, name);
        }
        
        IDispatcher dispatcher() {
            return this.dispatcher;
        }
    }
}
