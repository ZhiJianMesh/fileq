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
 * Concurrent message,
 * Processing order is not guaranteed
 * @author Lgy
 *
 */
public final class ConcurrentMessage implements IMessage {
    private final byte[] msg;
    private final int len;
    private final boolean passed;

    public ConcurrentMessage(int len, byte[] msg, boolean passed) {
        this.msg = msg;
        this.len = len;
        this.passed = passed;
    }
    
    @Override
    public byte[] message() {
        return msg;
    }

    @Override
    public int len() {
        return len;
    }

    @Override
    public boolean isCorrect() {
        return passed;
    }
}
