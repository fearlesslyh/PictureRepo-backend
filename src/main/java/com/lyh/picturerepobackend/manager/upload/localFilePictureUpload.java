package com.lyh.picturerepobackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static com.lyh.picturerepobackend.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/21 17:16
 */
@Service
public class localFilePictureUpload extends PictureUploadTemplate{
    @Override
    protected void validPicture(Object inputPicture) {
        MultipartFile multipartFile = (MultipartFile) inputPicture;
        ThrowUtils.throwIf(multipartFile.isEmpty(), PARAMS_ERROR, "图片不能为空");
        //1.校验文件大小
        long size = multipartFile.getSize();
        final long MAX_SIZE = 1024 * 1024 * 10;
        ThrowUtils.throwIf(size > MAX_SIZE, PARAMS_ERROR, "图片大小不能超过10M");

        //2.校验文件后缀
        String suffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        //3.允许上传的文件后缀
        final List<String> ALLOW_FORMAT_LIST = Arrays.asList("jpg", "jpeg", "png", "gif", "webp");
        ThrowUtils.throwIf(!ALLOW_FORMAT_LIST.contains(suffix), PARAMS_ERROR, "图片格式不支持");
    }

    @Override
    protected String getOriginalPictureName(Object inputPicture) {
        MultipartFile multipartFile = (MultipartFile) inputPicture;
        return multipartFile.getOriginalFilename();
    }

    @Override
    protected void processPicture(Object inputPicture, File file) throws Exception {
        MultipartFile multipartFile = (MultipartFile) inputPicture;
        multipartFile.transferTo(file);
    }
}
