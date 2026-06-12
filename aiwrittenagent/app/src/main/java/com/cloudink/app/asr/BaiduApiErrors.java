package com.cloudink.app.asr;

/**
 * 百度 API 错误码转中文说明（避免把「No permission to access data」误判为手机麦克风权限）。
 */
public final class BaiduApiErrors {

    private BaiduApiErrors() {}

    public static String formatAsrError(int errNo, String errMsg) {
        if (errNo == 3302) {
            return "百度语音应用未开通「语音识别」接口，或 baidu.asr 密钥填错。\n"
                + "请到控制台打开应用「云墨app03」(AppID " + BaiduAsrConfig.getAppId() + ")，"
                + "勾选「短语音识别」后保存，再重新编译安装。\n"
                + "原始信息: " + errMsg;
        }
        if (errNo == 3300 || errNo == 3301) {
            return "百度语音 API Key / Secret 无效，请核对 local.properties 中 baidu.asr 三项后重装 App";
        }
        if (errNo == 3307) {
            return "语音识别配额用尽或服务未启用，请在百度控制台查看「云墨app03」用量";
        }
        if (errNo == 6) {
            return "百度返回无数据访问权限(6)，请在控制台为「云墨app03」开通语音识别";
        }
        return "识别失败(" + errNo + "): " + errMsg;
    }

    public static String formatOcrError(int errNo, String errMsg) {
        if (errNo == 6 || errNo == 110 || errNo == 111) {
            return "百度 OCR 应用未开通「手写文字识别」或密钥错误。\n"
                + "请检查应用「云墨app02」(AppID " + com.cloudink.app.ocr.BaiduOcrConfig.getAppId() + ")。\n"
                + "原始信息: " + errMsg;
        }
        return "OCR 失败(" + errNo + "): " + errMsg;
    }

    /** 是否百度服务端权限/密钥类错误（不要当成 Android 麦克风权限）。 */
    public static boolean isBaiduServicePermissionError(String message) {
        if (message == null) return false;
        String m = message.toLowerCase();
        return m.contains("no permission to access data")
            || m.contains("未开通")
            || m.contains("err_no=3302")
            || m.contains("识别失败(3302")
            || m.contains("识别失败(6:")
            || m.contains("百度语音应用")
            || m.contains("百度 ocr");
    }
}
