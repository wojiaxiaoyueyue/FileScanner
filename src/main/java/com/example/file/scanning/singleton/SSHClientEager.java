package com.example.file.scanning.singleton;

import com.example.file.scanning.config.SSHClientConfig;
import net.schmizz.sshj.SSHClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * 类名称: SSHClientEager
 * 类描述: SSHClient单例类
 *
 * @author: qz
 * 创建时间: 2020/3/18 9:59 上午
 * Version 1.0
 */

@Component
public class SSHClientEager {

    private static final Logger log = LoggerFactory.getLogger(SSHClientEager.class);

    @Autowired
    SSHClientConfig config;


    private static SSHClient instance;


    public static SSHClient getInstance() {
        return instance;
    }

    @PostConstruct
    private void init() {
        instance = config.setupSshj();
    }

    @PreDestroy
    private void destory() {
        if (instance != null) {
            try {
                instance.disconnect();
            } catch (IOException e) {
                log.error("SSH连接关闭失败");
            }
        }
    }


}
