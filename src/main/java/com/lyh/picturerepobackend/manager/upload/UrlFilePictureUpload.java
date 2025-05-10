package com.lyh.picturerepobackend.manager.upload;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.lyh.picturerepo.infrastructure.exception.BusinessException;
import com.lyh.picturerepo.infrastructure.exception.ErrorCode;
import com.lyh.picturerepo.infrastructure.exception.ThrowUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import static com.lyh.picturerepo.infrastructure.exception.ErrorCode.PARAMS_ERROR;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/21 18:39
 */
@Service
public class UrlFilePictureUpload extends PictureUploadTemplate {
    /**
     * 校验输入源：url或本地文件
     *
     * @param inputPicture
     */
    @Override
    protected void validPicture(Object inputPicture) {
        String inputPictureNew = (String) inputPicture;
        ThrowUtils.throwIf(StrUtil.isBlank(inputPictureNew), PARAMS_ERROR, "图片地址不能为空");
        try {
            // 1. 验证 URL 格式
            new URL(inputPictureNew); // 验证是否是合法的 URL
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }
        ThrowUtils.throwIf(!(inputPictureNew.startsWith("http://") || inputPictureNew.startsWith("https://")), PARAMS_ERROR, "文件地址格式不正确");
        //通过发送head请求，判断文件是否存在
        HttpResponse response=null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, inputPictureNew).execute();
            //如果没有正常返回，说明文件不存在,无需继续执行
            ThrowUtils.throwIf(response.getStatus() != HttpStatus.HTTP_OK, PARAMS_ERROR, "文件不存在");
            //校验文件类型
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                final List<String> ALLOW_CONTENT_LIST = Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp","image/jpg");
                ThrowUtils.throwIf(!ALLOW_CONTENT_LIST.contains(contentType.toLowerCase()), PARAMS_ERROR, "文件类型不支持");
            }
            //校验文件大小
            String size = response.header("Content-Length");
            if (StrUtil.isNotBlank(size)) {
                try {
                    long contentLength = Long.parseLong(size);
                    final long TWO_MB = 2 * 1024 * 1024L; // 限制文件大小为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, PARAMS_ERROR, "文件大小不能超过 2Mb");
                }catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        }finally {
            if (response != null) {
                response.close();
            }
        }
    }
    /**
     * 获取原始图片名
     *
     * @param inputPicture
     * @return
     */
    @Override
    protected String getOriginalPictureName(Object inputPicture) {
        String inputPictureNew = (String) inputPicture;
        return FileUtil.getName(inputPictureNew);
    }
    /**
     * 处理图片并生成本地临时文件
     * @param inputPicture
     * @param file
     * @throws Exception
     */
    @Override
    protected void processPicture(Object inputPicture, File file) throws Exception {
        String inputPictureNew = (String) inputPicture;
        HttpUtil.downloadFile(inputPictureNew, file);
    }
}
