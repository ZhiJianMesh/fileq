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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

public interface IOutputStream extends IFile, Closeable {
    void write(byte[] content) throws IOException;
    void write(byte[] content, int offset, int len) throws IOException;
    /**
     * Force all content in buffer to be saved to disk right now
     * @throws IOException io exception
     */
    void flush() throws IOException;
    int size();
    File file();
}