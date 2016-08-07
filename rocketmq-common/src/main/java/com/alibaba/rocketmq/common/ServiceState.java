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
package com.alibaba.rocketmq.common;

/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 * 服务的状态
 */
public enum ServiceState {
    /**
     * Service just created,not start
     * 创建，未开始
     */
    CREATE_JUST,
    /**
     * Service Running
     * 运行
     */
    RUNNING,
    /**
     * Service shutdown
     * 关闭
     */
    SHUTDOWN_ALREADY,
    /**
     * Service Start failure
     * 失败
     */
    START_FAILED;
}
