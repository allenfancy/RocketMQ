package com.alibaba.rocketmq.common;

import java.net.InetAddress;
import java.util.List;

import org.junit.Test;


/**
 * @auther lansheng.zj@taobao.com
 */
public class MixAllTest {

    @Test
    public void test() throws Exception {
        List<String> localInetAddress = MixAll.getLocalInetAddress();
        String local = InetAddress.getLocalHost().getHostAddress();
        System.out.println(localInetAddress);
        System.out.println(local);
    }
    
    @Test
    public void testCreateBrokerId(){
    	long brokerId = MixAll.createBrokerId("192.168.1.174", 8080);
    	System.out.println(brokerId);
    }
}
