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
package com.alibaba.rocketmq.remoting.protocol;

import java.nio.charset.Charset;

import com.alibaba.fastjson.JSON;


/**
 * @description	远程序列化，使用FastJson ，toJson 和  fromJson
 * @author shijia.wxr<vintage.wang@gmail.com>
 * @since 2013-7-13
 * 
 */
public abstract class RemotingSerializable {
	
    public String toJson() {
        return toJson(false);
    }


    public String toJson(final boolean prettyFormat) {
        return toJson(this, prettyFormat);
    }

    /**
     * @description	将Object转为String
     * @param obj
     * @param prettyFormat
     * @return
     */
    public static String toJson(final Object obj, boolean prettyFormat) {
        return JSON.toJSONString(obj, prettyFormat);
    }

    /**
     * @description 		将String转为Object类型
     * @param json			字符串
     * @param classOfT	    需要转换的类型
     * @return
     */
    public static <T> T fromJson(String json, Class<T> classOfT) {
        return JSON.parseObject(json, classOfT);
    }

    /**
     * @description		编码
     * @return
     */
    public byte[] encode() {
        final String json = this.toJson();
        if (json != null) {
            return json.getBytes();
        }
        return null;
    }

    /**
     * 按字符编码为"UTF-8",编码为二进制
     * @param obj
     * @return
     */
    public static byte[] encode(final Object obj) {
        final String json = toJson(obj, false);
        if (json != null) {
            return json.getBytes(Charset.forName("UTF-8"));
        }
        return null;
    }

    /**
     * @description		将data字节码转为对象
     * @param data
     * @param classOfT
     * @return
     */
    public static <T> T decode(final byte[] data, Class<T> classOfT) {
        final String json = new String(data, Charset.forName("UTF-8"));
        return fromJson(json, classOfT);
    }
}
