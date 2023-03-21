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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Log utils, base on slf4j
 * @author Lgy
 *
 */
public final class LogUtil {
    private static final String FQCN = LogUtil.class.getName();
    
    private LogUtil() {
    }

    public static Logger getInstance() {
        return getInstance(getClassName());
    }

    public static Logger getInstance(String name) {
        return LoggerFactory.getLogger(name);
    }
    
    /**
     * Core function, get class name from stack trace
     * @return class name which LOG running in
     */
    private static String getClassName() {
        String logClass;
        StackTraceElement[] trace = Thread.currentThread().getStackTrace();
        for(int i = 2; i < trace.length; i++) {
            logClass = trace[i].getClassName();
            if(!logClass.equals(FQCN)) {
                return logClass; //return the last class name
            }
        }
        return FQCN;
    }
}
