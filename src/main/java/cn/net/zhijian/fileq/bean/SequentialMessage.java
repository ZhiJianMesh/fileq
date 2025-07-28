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
package cn.net.zhijian.fileq.bean;

import cn.net.zhijian.fileq.intf.IMessage;

/**
 * Sequence message
 * Must be handled one by one
 * @author Lgy
 *
 */
public final class SequentialMessage implements IMessage {
    private int len;
    private byte[] msg;
    private boolean passed;
    
    public SequentialMessage(int len) {
        this.len = len;
        this.msg = new byte[len];
    }
    
    @Override
    public int len() {
        return len;
    }
    
    @Override
    public byte[] message() {
        return msg;
    }
    
    public void setLen(int len) {
        this.len = len;
        if(len < msg.length) {
            return;
        }
        msg = new byte[len];
    }

    public void passed(boolean passed) {
        this.passed = passed;
    }
    
    @Override
    public boolean isCorrect() {
        // TODO Auto-generated method stub
        return passed;
    }
}
