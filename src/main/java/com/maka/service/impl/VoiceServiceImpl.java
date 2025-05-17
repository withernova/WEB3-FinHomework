package com.maka.service.impl;

import com.maka.service.VoiceService;
import com.maka.utils.CreateFeature;
import com.maka.utils.CreateGroup;
import com.maka.utils.SearchFeature;
import com.maka.config.XfyunConfig;
import com.maka.vo.SearchFeatureResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
public class VoiceServiceImpl implements VoiceService {

    private final XfyunConfig xfyunConfig;

    @Autowired
    public VoiceServiceImpl(XfyunConfig xfyunConfig) {
        this.xfyunConfig = xfyunConfig;
    }

    @Override
    public String createGroup() {
        CreateGroup createGroup = new CreateGroup(
                xfyunConfig.getRequestUrl(),
                xfyunConfig.getAppId(),
                xfyunConfig.getApiSecret(),
                xfyunConfig.getApiKey()
        );

        // 调用创建组的逻辑
        return createGroup.doCreateGroup();
    }


    @Override
    public String createFeature(String groupId, String featureId, String featureInfo, MultipartFile audioFile) {
        try {
            // 将 MultipartFile 转换为字节数组
            byte[] audioData = audioFile.getBytes();

            // 调用 CreateFeature 类，传入字节数组
            CreateFeature createFeature = new CreateFeature(
                    xfyunConfig.getRequestUrl(),
                    xfyunConfig.getAppId(),
                    xfyunConfig.getApiSecret(),
                    xfyunConfig.getApiKey(),
                    audioData,
                    groupId,
                    featureId,
                    featureInfo
            );

            return createFeature.doRequest();
        } catch (IOException e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @Override
    public SearchFeatureResult searchFeature(String groupId, int topK, MultipartFile audioFile) {
        try {
            byte[] audioData = audioFile.getBytes();

            // 创建 SearchFeature 对象，并执行特征比对
            SearchFeature searchFeature = new SearchFeature(
                    xfyunConfig.getRequestUrl(),
                    xfyunConfig.getAppId(),
                    xfyunConfig.getApiSecret(),
                    xfyunConfig.getApiKey(),
                    audioData
            );

            // 调用 doRequest 方法获取相似度和 featureInfo
            SearchFeatureResult result = searchFeature.doRequest(topK);

            return result;  // 直接返回 SearchFeatureResult 对象

        } catch (IOException e) {
            e.printStackTrace();
            return new SearchFeatureResult(0.0, "Error: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return new SearchFeatureResult(0.0, "Error: " + e.getMessage());
        }
    }
}
