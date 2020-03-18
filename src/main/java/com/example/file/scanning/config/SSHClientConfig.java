package com.example.file.scanning.config;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.UserAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * 类名称: SSHClientConfig
 * 类描述: SSH配置类
 *
 * @author: qz
 * 创建时间: 2020/3/18 9:48 上午
 * Version 1.0
 */

@Configuration
public class SSHClientConfig {

    private static final Logger log = LoggerFactory.getLogger(SSHClientConfig.class);

    private static final int TIME_OUT = 30_000;

    @Value("${remote.host}")
    String host;
    @Value("${remote.username}")
    String username;
    @Value("${remote.password}")
    String password;
    @Value("${remote.dir}")
    String dir;

    public String getDir() {
        return dir;
    }

    public SSHClient setupSshj() {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.setConnectTimeout(TIME_OUT);
        while (!client.isConnected() || !client.isAuthenticated()) {
            try {
                client.connect(host);
                client.authPassword(username, password);
            } catch (UserAuthException userException) {
                log.error("SSH连接中用户校验时出现异常", userException);
            } catch (SocketTimeoutException socketException) {
                log.error("SSH连接超时，请核实远程服务器的用户名与密码", socketException);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        log.info("SSH已经创建连接成功");
        return client;
    }





}
