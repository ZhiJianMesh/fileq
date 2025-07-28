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
 * Message
 * @author Lgy
 *
 */
public interface IMessage {
    /**
     * Message body
     * @return body Message body, must use len() to identify its length
     */
    byte[] message();
    /**
     * The real message length
     * @return length Real length of message body
     */
    int len();
    /**
     * Passed the hash code checking or not if hash-code enabled when push it.
     * Correct and incorrect messages all will be delivered to consumers.
     * Consumers need to decide how to handle it.
     * @return true if passed
     */
    boolean isCorrect();   
}
