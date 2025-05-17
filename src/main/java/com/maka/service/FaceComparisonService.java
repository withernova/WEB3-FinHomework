package com.maka.service;
import org.springframework.web.multipart.MultipartFile;
public interface FaceComparisonService {
    String compareFace(MultipartFile file) throws Exception;
    String compareFaceFromVideo(MultipartFile file) throws Exception;
}
