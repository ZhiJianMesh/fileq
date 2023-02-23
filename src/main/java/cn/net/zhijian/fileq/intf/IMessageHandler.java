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
 * Message handler
 * @author Lgy
 *
 */
public interface IMessageHandler {
    /**
     * handle message, IReader.confirm should be called finally
     * @param msg
     *  process msg one by one of a queue,
     *  if return true, will handle the next one,
     *  or will not confirm the message
     * @param writer
     *  If failed to handle the message, you can write it back
     */
    public boolean handle(IMessage msg);
}
