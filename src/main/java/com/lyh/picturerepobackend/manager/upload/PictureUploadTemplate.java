package com.lyh.picturerepobackend.manager.upload;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.lyh.picturerepobackend.config.CosConfig;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.manager.CosManager;
import com.lyh.picturerepobackend.model.dto.file.FileUpload;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.File;
import java.util.Objects;

import static com.lyh.picturerepobackend.exception.ErrorCode.SYSTEM_ERROR;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/21 16:40
 */
@Slf4j
public abstract class PictureUploadTemplate {
    @Resource
    protected CosManager cosManager;
    @Resource
    protected CosConfig cosConfig;

    /**
     * 模板方法，定义上传流程
     *
     * @param inputPicture     各种图片上传方式，如url或本地文件
     * @param uploadPathPrefix 图片上传路径前缀
     * @return
     * @throws Exception
     * 为了让模板同时兼容url和本地文件上传，这里使用Object类型接收输入源
     */

    public final FileUpload uploadPicture(Object inputPicture, String uploadPathPrefix) {
        //1.校验图片
        validPicture(inputPicture);
        //2.图片上传地址
        String uuid = RandomUtil.randomString(16);
        String originalPictureName = getOriginalPictureName(inputPicture);
        String uploadFileName = String.format("%s_%s.%s", DateUtil.date(),uuid, (Objects.equals(FileUtil.getSuffix(originalPictureName), ""))?"jpg":FileUtil.getSuffix(originalPictureName));
        String uploadPath = String.format("%s/%s", uploadPathPrefix, uploadFileName);
        File file =null;
        try {
            // 创建无路径前缀的临时文件
            file = File.createTempFile(uuid, null);
            processPicture(inputPicture, file);
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            return buildFileUpload(file, originalPictureName, uploadPath, imageInfo);

        }catch (Exception e){
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(SYSTEM_ERROR, "图片上传失败，请稍后重试");
        }finally {
            //清理临时文件
            deleteTempFile(file);
        }
    }

    /**
     * 校验输入源：url或本地文件
     *
     * @param inputPicture
     */
    protected abstract void validPicture(Object inputPicture);

    /**
     * 获取原始图片名
     *
     * @param inputPicture
     * @return
     */
    protected abstract String getOriginalPictureName(Object inputPicture);

    /**
     * 处理图片并生成本地临时文件
     * @param inputPicture
     * @param file
     * @throws Exception
     */

    protected abstract void processPicture(Object inputPicture, File file) throws Exception;

    /**
     * 封装返回结果
     * @param file
     */
    private FileUpload buildFileUpload(File file, String originalPictureName, String uploadPath, ImageInfo imageInfo) {
        FileUpload fileUpload = new FileUpload();
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        double picScale= NumberUtil.round((double) width / height, 2).doubleValue();
        fileUpload.setUrl(cosConfig.getHost()+"/" + uploadPath);
        fileUpload.setPicName(FileUtil.getName(originalPictureName));
        fileUpload.setPicSize(FileUtil.size(file));
        fileUpload.setPicWidth(width);
        fileUpload.setPicHeight(height);
        fileUpload.setPicScale(picScale);
        fileUpload.setPicFormat(imageInfo.getFormat());
        return fileUpload;
    }

    /**
     * 删除临时文件
     */
    public void deleteTempFile(File file) {
        if (file == null) {
            return;
        }
        // 删除临时文件
        boolean deleteResult = file.delete();
        if (!deleteResult) {
            log.error("文件删除失败, 文件路径为 = {}", file.getAbsolutePath());
        }
    }
}
