package com.example.file.scanning.singleton;

/**
 * 枚举名称: WaitTimeSensitivity
 * 枚举描述: 文件上传灵敏度等级
 *
 * @author: qz
 * 创建时间: 2020/3/18 10:11 上午
 * Version 1.0
 */

public enum WaitTimeSensitivity {
    HIGH(10),
    MIDEUM(20),
    LOW(30);

    /** 灵敏度 .**/
    private final int sensitivity;

    WaitTimeSensitivity(int sensitivity) {
        this.sensitivity = sensitivity;
    }

    public int getSensitivity() {
        return sensitivity;
    }
}
