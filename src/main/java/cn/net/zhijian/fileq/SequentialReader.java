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
import cn.net.zhijian.fileq.intf.IMessage;
import cn.net.zhijian.fileq.intf.IWriter;

/**
 * Sequential consumer, consume one by one.
 * If consume is not confirmed, 
 * just return the old message until it was confirmed 
 * @author Lgy
 *
 */
class SequentialReader extends ConcurrentReader {
    private static final int MIN_RETRY_INTERVAL = 500;
    private static final int MAX_RETRY_INTERVAL = 10 * 1000;
    
    //The state of one message
    private static enum MsgState {
        IDLE, //no message
        WAITCONFIRM, //the last message handled, but not confirmed
        FAILED //fail to handle the last message
    };
    
    private int retryInterval = MIN_RETRY_INTERVAL; //ms
    private long retriedAt; //ms，上次尝试时间点
    private SequentialMessage msg = new SequentialMessage(DEFAULT_BUF_LEN);
    protected MsgState state = MsgState.IDLE;
    
    /**
     * @param name consumer name
     * @param writer
     * @param buffered buffered mode
     * @throws IOException
     */
    public SequentialReader(String name, IWriter writer, boolean buffered) throws IOException {
        super(name, writer, buffered);
    }

    /**
     * should 
     */
    @Override
    public IMessage read() {
        if(state == MsgState.WAITCONFIRM) {
            return null;
        }
        
        if(state == MsgState.FAILED) {
            long cur = System.currentTimeMillis();
            if(cur - retriedAt > retryInterval) {
                if(retryInterval < MAX_RETRY_INTERVAL) {
                    retryInterval <<= 1; //double retry time
                }
                retriedAt = cur;
                return msg; //return old message again
            }
            return null;
        }
        retryInterval = MIN_RETRY_INTERVAL;
        retriedAt = System.currentTimeMillis();
        IMessage msg = super.read();
        state = msg != null ? MsgState.WAITCONFIRM : MsgState.IDLE;
        
        return msg;
    }
    
    @Override
    protected byte[] getBuffer(int len) {
        msg.setLen(len);
        //reuse buffer, because messages are handled one by one.
        return msg.message();
    }
    
    @Override
    protected IMessage generateMessage(int len, byte[] content) {
        return msg;
    }
    
    @Override
    public void confirm(boolean ok) {
        if(ok) {
            state = MsgState.IDLE;
            if(qFile != null) {
                this.consumeState.save(qFile.readPos(), false);
            }
        } else{
            state = MsgState.FAILED;
        }
    }
    
    @Override
    public boolean needNotifyReady() {
        return true;
    } 
}