package com.example.file.scanning.pool;

import com.example.file.scanning.singleton.SSHClientEager;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.SFTPClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * 类名称: SFTPClientPool
 * 类描述: SFTP客户端连接池
 * 存在以下缺陷：
 * 1. 没有动态增长与收缩方法
 * 2. 不能保证连接的时刻可用性
 *
 * @author: qz
 * 创建时间: 2020/3/18 9:58 上午
 * Version 1.0
 */

public class SFTPClientPool {
    private static final Logger log = LoggerFactory.getLogger(SFTPClientPool.class);

    /**
     * 连接池大小 .
     **/
    private final int poolSize;

    /**
     * 连接对象数组 .
     **/
    private SFTPClient[] connections;

    /**
     * 连接状态数组 0表示空闲， 1表示繁忙 .
     **/
    private AtomicIntegerArray states;

    SSHClient sshClient = SSHClientEager.getInstance();

    public SFTPClientPool(int poolSize) {
        this.poolSize = poolSize;
        this.connections = new SFTPClient[poolSize];
        this.states = new AtomicIntegerArray(new int[poolSize]);
        for (int i = 0; i < poolSize; i++) {
            try {
                connections[i] = sshClient.newSFTPClient();
            } catch (IOException e) {
                log.error("SFTPClient 连接创建失败:", e);
            }
        }
    }

    public int getIndex(SFTPClient client) {
        for (int i = 0; i < poolSize; i++) {
            if (connections[i] == client) {
                return i;
            }
        }
        return -1;
    }


    public SFTPClient take() {
        for (;;) {
            for (int i = 0; i < poolSize; i++) {
                if (states.get(i) == 0) {
                    if (states.compareAndSet(i, 0, 1)) {
                        log.info("使用第: {}个SFTP连接进行传输", i);
                        return connections[i];
                    }
                }
            }
            // 如果空闲连接为空，当前线程等待
            synchronized (this) {
                try {
                    log.info("暂无可用SFTP连接，线程执行wait()方法");
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void free(SFTPClient sftpClient) {
        for (int i = 0; i < poolSize; i++) {
            if (connections[i] == sftpClient) {
                states.set(i, 0);
                synchronized (this) {
                    log.info("释放第: {}个SFTP连接", i);
                    this.notifyAll();
                }
                break;
            }
        }
    }

    public void close() {
        for (int i = 0; i < poolSize; i++) {
            if (connections[i] != null) {
                try {
                    connections[i].close();
                } catch (IOException e) {
                    log.error("关闭第: {}个SFTP连接失败", i);
                }
            }
        }
    }

}
