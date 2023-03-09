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
package cn.net.zhijian.fileq.util;

import java.io.File;

/**
 * File utils
 * @author Lgy
 *
 */
public final class FileUtil {
    public static String addPath(String path, String name) {
        int len = path.length();
        char ch = path.charAt(len - 1);
        String p = (ch == '/' || ch == '\\') ? path.substring(0, len - 1) : path;
        ch = name.charAt(0);
        String n = (ch == '/' || ch == '\\') ? name.substring(1) : name;
        return p + File.separatorChar + n;
    }
    
    public static void closeQuietly(AutoCloseable o) {
        if(o == null) {
            return;
        }
        try {
            o.close();
        } catch(Exception e) {
        }
    }
}
