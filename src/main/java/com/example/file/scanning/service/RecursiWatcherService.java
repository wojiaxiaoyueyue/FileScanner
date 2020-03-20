package com.example.file.scanning.service;

import com.example.file.scanning.singleton.WaitTimeSensitivity;
import com.example.file.scanning.util.SSHClientUtil;
import com.sun.nio.file.SensitivityWatchEventModifier;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static java.nio.file.StandardWatchEventKinds.*;

/**
 * 类名称: RecursiWatcherService
 * 类描述: 文件扫描服务类
 *
 * @author: qz
 * 创建时间: 2020/3/18 9:54 上午
 * Version 1.0
 */

@Service
public class RecursiWatcherService {

    private static final Logger log = LoggerFactory.getLogger(RecursiWatcherService.class);


    @Value("${local.folder}")
    private File localFolder;

    @Autowired
    SSHClientUtil client;

    private WatchService watcher;

    private ExecutorService scanExecutor;

    private ExecutorService sftpExecutor;

    private List<FileObject> list = new ArrayList<>();



    @PostConstruct
    public void init() throws IOException {
        watcher = FileSystems.getDefault().newWatchService();
        scanExecutor = Executors.newSingleThreadExecutor();
        sftpExecutor = Executors.newSingleThreadExecutor();
        startRecursiveWatcher();
        startUploadWatcher();
    }

    @PreDestroy
    public void cleanup() {
        try {
            watcher.close();
        } catch (IOException e) {
            log.error("Error closing watcher service", e);
        }
        scanExecutor.shutdown();
        sftpExecutor.shutdown();
    }

    private void startRecursiveWatcher() {
        log.info("Starting Recursive Watcher");

        final Map<WatchKey, Path> keys = new HashMap<>();

        Consumer<Path> register = p -> {
            if (!p.toFile().exists() || !p.toFile().isDirectory()) {
                throw new RuntimeException("folder " + p + " does not exist or is not a directory");
            }
            try {
                // JDK7 提供遍历文件夹的方式
                Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        log.info("registering " + dir + " in watcher service");
                        WatchKey watchKey = dir.register(watcher, new WatchEvent.Kind[]{ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE}, SensitivityWatchEventModifier.MEDIUM);
                        keys.put(watchKey, dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new RuntimeException("Error registering path " + p);
            }
        };

        register.accept(localFolder.toPath());

        scanExecutor.submit(() -> {
            while (true) {
                final WatchKey key;
                try {
                    key = watcher.take(); // wait for a key to be available. current thread would be blocked.
                } catch (InterruptedException ex) {
                    return;
                }

                final Path dir = keys.get(key);
                if (dir == null) {
                    System.err.println("WatchKey " + key + " not recognized!");
                    continue;
                }

                List<WatchEvent<?>> eventList = key.pollEvents();

                for (WatchEvent<?> event : eventList) {
                    WatchEvent.Kind<?> type = event.kind();
                    final Path changed = (Path) event.context();
                    Path absPath = dir.resolve(changed);
                    final String absPathStr = absPath.toAbsolutePath().toString();
                    final String relativePath = StringUtils.remove(absPathStr, localFolder.getAbsolutePath());
                    boolean isDir = absPath.toFile().isDirectory();
                    if (type == OVERFLOW) {

                    } else if (type == ENTRY_CREATE) {
                        log.info("Detected new file: isDirectory(): {}, absolutePath: {}", isDir, absPathStr);
                        if (isDir) {
                            register.accept(absPath);
                        }
                        list.add(new FileObject(absPathStr, relativePath));
                    } else if (type == ENTRY_MODIFY) {
                        log.info("Detected file modify: isDirectory(): {}, absolutePath: {}", isDir, absPathStr);
                        if (isDir) {

                        } else {
                            list.add(new FileObject(absPathStr, relativePath));
                        }
                    } else {
                        log.info("Detected file delete: isDirectory(): {}, absolutePath: {}", isDir, absPathStr);
                    }

                }

                boolean valid = key.reset(); // IMPORTANT: The key must be reset after processed
                if (!valid) {
                    keys.remove(key);
                    // all directories are inaccessible
                    if (keys.isEmpty()) {
                        break;
                    }
                }
            }
        });
    }

    private void startUploadWatcher() {
        log.info("Starting upload Watcher");
        // 上次迭代中size
        AtomicInteger size = new AtomicInteger(list.size());
        sftpExecutor.submit(() -> {
            while (true) {
                // 等待文件上传完毕
                TimeUnit.SECONDS.sleep(WaitTimeSensitivity.MIDEUM.getSensitivity());
                if (list.size() == 0) {
                    //
                } else if (list.size() > size.get()) {
                    size.compareAndSet(size.get(), list.size());
                } else {    // list.size() == size.get()
                    Set<FileObject> set = list.stream().collect(Collectors.toSet());
                    log.info("size: {}, set size: {}", list.size(), set.size());
                    set.forEach(s -> client.starUploadJob(s.getAbsPath(), s.getRelativePath()));
                    list = new ArrayList<>();
                }
            }
        });

    }

    class FileObject {
        private String absPath;

        private String relativePath;

        public FileObject(String absPath, String relativePath) {
            this.absPath = absPath;
            this.relativePath = relativePath;
        }

        public String getAbsPath() {
            return absPath;
        }

        public String getRelativePath() {
            return relativePath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FileObject object = (FileObject) o;
            return Objects.equals(absPath, object.absPath) &&
                    Objects.equals(relativePath, object.relativePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(absPath, relativePath);
        }
    }
}
