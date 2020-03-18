package com.example.file.scanning.util;

import com.example.file.scanning.config.SSHClientConfig;
import com.example.file.scanning.pool.SFTPClientPool;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.sftp.SFTPException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 类名称: SSHClientUtil
 * 类描述: SSH客户端工具类
 *
 * @author: qz
 * 创建时间: 2020/3/18 10:02 上午
 * Version 1.0
 */

@Component
public class SSHClientUtil {
    private static final Logger log = LoggerFactory.getLogger(SSHClientUtil.class);

    @Autowired
    SSHClientConfig config;

    /**
     * SFTP连接池 .
     **/
    SFTPClientPool pool;

    String NO_SUCH_FILE = "No such file";

    private static int POOL_SIZE = 3;
    private static int THREAD_SIZE = 4;

    ExecutorService executorService;

    private ExecutorService getExecutorService() {
        if (executorService == null)
            executorService = Executors.newFixedThreadPool(THREAD_SIZE, getFactory());
        return executorService;
    }

    public ThreadFactory getFactory() {
        return new ThreadFactory() {
            private AtomicInteger i = new AtomicInteger();
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "sftp-upload-" + i.incrementAndGet());
            }
        };
    }

    private SFTPClientPool getPool() {
        if (pool != null) {
            return pool;
        } else {
            synchronized (this) {
                if (pool == null) {
                    pool = new SFTPClientPool(POOL_SIZE);
                }
            }
        }
        return pool;
    }

    private void uploadFile(SFTPClientPool pool, String absolutePath, String relativePath) {
        SFTPClient sftpClient = null;
        String dest = null;
        try {
            sftpClient = pool.take();
            dest = config.getDir() + relativePath;
            log.info("第{}: 个SFTP 正在执行文件上传， 本地路径为: {}, 远程路径为: {}", pool.getIndex(sftpClient), absolutePath, dest);
            sftpClient.put(absolutePath, dest);
        } catch (SFTPException e) {
            mkdir(absolutePath, sftpClient, dest, e);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            pool.free(sftpClient);
        }
    }

    private void mkdir(String absolutePath, SFTPClient sftpClient, String dest, SFTPException e) {
        if (StringUtils.equals(NO_SUCH_FILE, e.getMessage())) {
            // 不存在文件夹时会出现SFTPException错误，需要手动创建
            String remoteDirName = dest.substring(0, dest.lastIndexOf(File.separator));
            try {
                sftpClient.mkdirs(remoteDirName);
                sftpClient.put(absolutePath, dest);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * 异步形式启动任务，默认线程池大小为3
     *
     * @param absolutePath 部署所在服务器文件的绝对路径
     * @param relativePath 远程服务器相对路径
     */
    public void starUploadJob(String absolutePath, String relativePath) {
        SFTPClientPool pool = getPool();
        CompletableFuture.runAsync(() -> uploadFile(pool, absolutePath, relativePath), getExecutorService());
    }
}
