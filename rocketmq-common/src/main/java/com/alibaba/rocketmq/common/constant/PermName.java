/**
 * Copyright (C) 2010-2013 Alibaba Group Holding Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.rocketmq.common.constant;

/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 * <p>
 *  0x 表示16进制
 * </p>
 */
public class PermName {
	
    public static final int PERM_PRIORITY = 0x1 << 3; //8 优先
    public static final int PERM_READ = 0x1 << 2; // 4 读
    public static final int PERM_WRITE = 0x1 << 1; // 2 写
    public static final int PERM_INHERIT = 0x1 << 0; // 1继承

    //是否可读
    public static boolean isReadable(final int perm) {
        return (perm & PERM_READ) == PERM_READ;
    }

    //是否可写
    public static boolean isWriteable(final int perm) {
        return (perm & PERM_WRITE) == PERM_WRITE;
    }

    //是否继承
    public static boolean isInherited(final int perm) {
        return (perm & PERM_INHERIT) == PERM_INHERIT;
    }

    //将perm转为String
    public static String perm2String(final int perm) {
        final StringBuffer sb = new StringBuffer("---");
        if (isReadable(perm)) {
            sb.replace(0, 1, "R");
        }
        if (isWriteable(perm)) {
            sb.replace(1, 2, "W");
        }
        if (isInherited(perm)) {
            sb.replace(2, 3, "X");
        }
        return sb.toString();
    }
}
