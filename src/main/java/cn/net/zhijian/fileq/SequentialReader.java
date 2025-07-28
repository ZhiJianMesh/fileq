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

import cn.net.zhijian.fileq.bean.SequentialMessage;
import cn.net.zhijian.fileq.intf.IDispatcher;
import cn.net.zhijian.fileq.intf.IMessage;
import cn.net.zhijian.fileq.intf.IWriter;

/**
 * Sequential consumer, consume one by one.
 * If consume is not confirmed, 
 * just return the old message until it was confirmed 
 * @author Lgy
 *
 */
final class SequentialReader extends ConcurrentReader {
    private static final int MIN_RETRY_INTERVAL = 500;
    private static final int MAX_RETRY_INTERVAL = (MIN_RETRY_INTERVAL << 5);
    private static final int MAX_FAILED_TIMES = 10;
    
    //The state of the queue's consumer
    private enum MsgState {
        IDLE, //no message
        WAITCONFIRM, //the last message handled, but not confirmed
        FAILED //fail to handle the last message
    }
    
    private int retryInterval = MIN_RETRY_INTERVAL; //ms
    private int failedTimes = 0;
    private long retriedAt; //ms, fore retry time
    private final IDispatcher dispatcher;

    private final SequentialMessage msg = new SequentialMessage(DEFAULT_BUF_LEN);
    private MsgState state = MsgState.IDLE;
    
    /**
     * @param name Consumer name
     * @param writer Message writer
     * @param dispatcher Message dispatcher
     * @param buffered Buffered mode
     * @param bufferedPos
     *  Save consume-position to disk after `bufferedPos` times updating
     * @param pos Initial position(CUR,HEAD,END)
     * @throws IOException io exception when open the queue
     */
    public SequentialReader(String name, IWriter writer, IDispatcher dispatcher,
            boolean buffered, int bufferedPos, InitPosition pos) throws IOException {
        super(name, writer, buffered, bufferedPos, pos);
        this.dispatcher = dispatcher;
    }

    /**
     * read a message from queue in a single thread
     */
    @Override
    public IMessage read() {
        if(state == MsgState.WAITCONFIRM) {
            return null;
        }
        
        IMessage msg = null;
        if(state == MsgState.FAILED) {
            long cur = System.currentTimeMillis();
            if(cur - retriedAt > retryInterval) {
                if(retryInterval < MAX_RETRY_INTERVAL) {
                    retryInterval <<= 1; //double retry time
                }
                retriedAt = cur;
                if(failedTimes >= MAX_FAILED_TIMES) {
                    //when using FastInputStream in high concurrency scenarios,
                    //sometimes get unexpected content.
                    //In ConcurrentRead, there's no chance to undo the mistake when it happen
                    msg = super.reRead();
                } else {
                    msg = this.msg; //return old message again
                }
                state = MsgState.WAITCONFIRM;
            }
        } else {
            retryInterval = MIN_RETRY_INTERVAL;
            retriedAt = System.currentTimeMillis();
            msg = super.read();
            state = msg != null ? MsgState.WAITCONFIRM : MsgState.IDLE;
        }

        return msg;
    }
    
    @Override
    protected byte[] getBuffer(int len) {
        msg.setLen(len);
        //reuse buffer, because messages are handled one by one.
        return msg.message();
    }
    
    @Override
    protected IMessage generateMessage(int len, byte[] content, boolean passed) {
        msg.passed(passed);
        return msg;
    }
    
    @Override
    public void confirm(boolean ok) {
        if(ok) {
            state = MsgState.IDLE;
            failedTimes = 0; //blocked at the failed one, so directly set to 0 when ok
            if(qFile != null) {
                this.consumeState.save(qFile.readPos(), false);
            }
        } else{
            state = MsgState.FAILED;
            failedTimes++;
        }
        /*
         * In sequential reader, one message confirmed,
         * then handle the next one. So active the loop right now.
         */
        dispatcher.ready();
    }
}
