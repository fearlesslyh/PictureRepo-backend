package com.lyh.picturerepobackend.manager;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.model.dto.file.FileUpload;
import com.lyh.picturerepobackend.model.enums.FileUploadBizEnum;
import com.lyh.picturerepobackend.model.enums.ImageFormatEnum;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.lyh.picturerepobackend.config.CosConfig;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;

/**
 * 文件管理类
 * 负责处理文件上传、删除等操作，支持不同业务类型的上传路径和校验规则
 */
@Service
public class FileManager {

    private static final Logger log = LoggerFactory.getLogger(FileManager.class);

    @Resource
    private CosConfig cosConfig;

    @Resource
    private COSClient cosClient;

    @Resource
    private CosManager cosManager;

    /**
     * 上传文件到 COS
     *
     * @param multipartFile 要上传的文件
     * @param bizCode       业务类型，使用 FileUploadBizEnum 中的值
     * @return FileUpload    包含文件信息的对象
     * @throws BusinessException 上传失败时抛出
     */
    public FileUpload uploadFile(MultipartFile multipartFile, String bizCode) throws BusinessException {
        // 1. 获取业务类型枚举
        FileUploadBizEnum bizEnum = FileUploadBizEnum.getByCode(bizCode);
        log.info("开始上传文件，业务类型：{}", bizEnum.getCode());

        // 2. 校验文件
        validFile(multipartFile, bizEnum);

        // 3. 生成上传路径和文件名
        String uuid = UUID.randomUUID().toString().replaceAll("-", ""); // 使用UUID作为文件名，避免重复
        String originFilename = multipartFile.getOriginalFilename();
        String fileExtension = FileUtil.getSuffix(originFilename); // 获取文件后缀名
        String uploadFilename = String.format("%s_%s.%s", DateUtil.format(new Date(), "yyyyMMdd"), uuid, fileExtension); // 包含日期
        String uploadPath = String.format("%s/%s", bizEnum.getPathPrefix(), uploadFilename); // 完整的上传路径，例如：picture/20240724_xxxxxxxx.jpg

        try {
            // 4. 上传文件到 COS
            String fileUrl = uploadFileToCos(multipartFile, uploadPath);
            log.info("文件上传成功，URL：{}", fileUrl);

            // 5. 封装并返回文件信息
            return packageFileUploadResult(multipartFile, originFilename, fileUrl);

        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败，请稍后重试"); // 统一异常处理
        }
    }

    /**
     * 根据图片url上传
     * @param fileUrl 图片url
     * @param uploadPathPrefix
     * @return
     */
    public FileUpload uploadPictureByUrl(String fileUrl, String uploadPathPrefix) {
        // 校验图片
        // validPicture(multipartFile);
        validPicture(fileUrl);
        // 图片上传地址
        String uuid = RandomUtil.randomString(16);
        // String originFilename = multipartFile.getOriginalFilename();
        String originFilename = FileUtil.mainName(fileUrl);
        String uploadFilename = String.format("%s_%s.%s", DateUtil.formatDate(new Date()), uuid,
                FileUtil.getSuffix(originFilename));
        String uploadPath = String.format("/%s/%s", uploadPathPrefix, uploadFilename);
        File file = null;
        try {
            // 创建临时文件
            file = File.createTempFile(uploadPath, null);
            // multipartFile.transferTo(file);
            HttpUtil.downloadFile(fileUrl, file);
            // 上传图片
            // 上传图片
            PutObjectResult putObjectResult = cosManager.putPictureObject(uploadPath, file);
            ImageInfo imageInfo = putObjectResult.getCiUploadResult().getOriginalInfo().getImageInfo();
            // 封装返回结果
            FileUpload uploadPictureResult = new FileUpload();
            int picWidth = imageInfo.getWidth();
            int picHeight = imageInfo.getHeight();
            double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
            uploadPictureResult.setPicName(FileUtil.mainName(originFilename));
            uploadPictureResult.setPicWidth(picWidth);
            uploadPictureResult.setPicHeight(picHeight);
            uploadPictureResult.setPicScale(picScale);
            uploadPictureResult.setPicFormat(imageInfo.getFormat());
            uploadPictureResult.setPicSize(FileUtil.size(file));
            uploadPictureResult.setUrl(cosConfig.getHost() + "/" + uploadPath);
            return uploadPictureResult;
        } catch (Exception e) {
            log.error("图片上传到对象存储失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            this.deleteTempFile(file);
        }
    }


    /**
     * 将文件直接上传到COS，不经过本地存储
     *
     * @param multipartFile 上传的文件
     * @param uploadPath    文件上传到COS的路径
     * @return 文件URL
     * @throws IOException
     */
    private String uploadFileToCos(MultipartFile multipartFile, String uploadPath) throws IOException {
        InputStream inputStream = multipartFile.getInputStream();
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(multipartFile.getSize());
        objectMetadata.setContentType(multipartFile.getContentType());  // 设置 Content-Type

        // 创建上传请求
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucketName(), uploadPath, inputStream, objectMetadata);
        cosClient.putObject(putObjectRequest); // 上传
        inputStream.close();
        return cosConfig.getHost() + "/" + uploadPath;
    }

    /**
     * 校验上传的文件
     *
     * @param multipartFile 要校验的文件
     * @param bizEnum       业务类型枚举，包含校验规则
     * @throws BusinessException 如果文件不符合规范，抛出异常
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum bizEnum) throws BusinessException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        long fileSize = multipartFile.getSize();
        if (fileSize > bizEnum.getMaxFileSize()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小超过限制 (最大" + bizEnum.getMaxFileSize() / 1024 / 1024 + "MB)");
        }

        String contentType = multipartFile.getContentType();
        if (!Arrays.asList(bizEnum.getAllowedContentTypes()).contains(contentType) && !Arrays.asList(bizEnum.getAllowedContentTypes()).contains("*")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误，允许的类型：" + Arrays.toString(bizEnum.getAllowedContentTypes()));
        }

        // 针对图片上传的额外校验
        if (bizEnum.getCode().equals(FileUploadBizEnum.PICTURE.getCode())) {
            String fileExtension = FileUtil.getSuffix(multipartFile.getOriginalFilename());
            ImageFormatEnum imageFormat = ImageFormatEnum.getByExtension(fileExtension);
            if (imageFormat == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片格式，只支持" + Arrays.toString(ImageFormatEnum.values()));
            }
            if (!contentType.equalsIgnoreCase(imageFormat.getContentType())) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "Content-Type 不匹配，应为 " + imageFormat.getContentType());
            }
        }
    }

    private void validPicture(String fileUrl) {
        ThrowUtils.throwIf(StrUtil.isBlank(fileUrl), ErrorCode.PARAMS_ERROR, "文件地址不能为空");

        try {
            // 1. 验证 URL 格式
            new URL(fileUrl); // 验证是否是合法的 URL
        } catch (MalformedURLException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件地址格式不正确");
        }

        // 2. 校验 URL 协议
        ThrowUtils.throwIf(!(fileUrl.startsWith("http://") || fileUrl.startsWith("https://")),
                ErrorCode.PARAMS_ERROR, "仅支持 HTTP 或 HTTPS 协议的文件地址");

        // 3. 发送 HEAD 请求以验证文件是否存在
        HttpResponse response = null;
        try {
            response = HttpUtil.createRequest(Method.HEAD, fileUrl).execute();
            // 未正常返回，无需执行其他判断
            if (response.getStatus() != HttpStatus.HTTP_OK) {
                return;
            }
            // 4. 校验文件类型
            String contentType = response.header("Content-Type");
            if (StrUtil.isNotBlank(contentType)) {
                // 允许的图片类型
                final List<String> ALLOW_CONTENT_TYPES = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/webp");
                ThrowUtils.throwIf(!ALLOW_CONTENT_TYPES.contains(contentType.toLowerCase()),
                        ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
            // 5. 校验文件大小
            String contentLengthStr = response.header("Content-Length");
            if (StrUtil.isNotBlank(contentLengthStr)) {
                try {
                    long contentLength = Long.parseLong(contentLengthStr);
                    final long TWO_MB = 2 * 1024 * 1024L; // 限制文件大小为 2MB
                    ThrowUtils.throwIf(contentLength > TWO_MB, ErrorCode.PARAMS_ERROR, "文件大小不能超过 2Mb");
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小格式错误");
                }
            }
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }


    /**
     * 封装文件上传结果
     *
     * @param multipartFile  上传的文件
     * @param originFilename 原始文件名
     * @param fileUrl        文件 URL
     * @return FileUpload    包含文件信息的对象
     */
    private FileUpload packageFileUploadResult(MultipartFile multipartFile, String originFilename, String fileUrl) {
        FileUpload fileUploadResult = new FileUpload();
        fileUploadResult.setUrl(fileUrl);
        fileUploadResult.setPicName(FileUtil.mainName(originFilename));
        fileUploadResult.setPicSize(multipartFile.getSize());
        try {
            // 获取图片信息
            InputStream inputStream = multipartFile.getInputStream();
            ObjectMetadata objectMetadata = cosClient.getObjectMetadata(cosConfig.getBucketName(), fileUrl);
            inputStream.close();
            if (objectMetadata != null) {
                String width = objectMetadata.getRawMetadata().get("x-image-width") != null ? objectMetadata.getRawMetadata().get("x-image-width").toString() : null;
                String height = objectMetadata.getRawMetadata().get("x-image-height") != null ? objectMetadata.getRawMetadata().get("x-image-height").toString() : null;
                String format = objectMetadata.getRawMetadata().get("x-image-format") != null ? objectMetadata.getRawMetadata().get("x-image-format").toString() : null;

                if (StringUtils.isNotBlank(width) && StringUtils.isNotBlank(height)) {
                    int picWidth = Integer.parseInt(width);
                    int picHeight = Integer.parseInt(height);
                    double picScale = NumberUtil.round(picWidth * 1.0 / picHeight, 2).doubleValue();
                    fileUploadResult.setPicWidth(picWidth);
                    fileUploadResult.setPicHeight(picHeight);
                    fileUploadResult.setPicScale(picScale);
                }
                if (StringUtils.isNotBlank(format)) {
                    fileUploadResult.setPicFormat(format);
                }
            }
        } catch (Exception e) {
            log.error("获取图片信息失败: {}", e.getMessage());
            //  可以选择抛出异常，或者记录日志后继续，这里选择记录日志后继续
        }
        return fileUploadResult;
    }

    /**
     * 从文件 URL 中提取文件名
     *
     * @param fileUrl 文件 URL
     * @return 文件名
     */
    private String extractFilenameFromUrl(String fileUrl) {
        if (StringUtils.isBlank(fileUrl)) {
            return null;
        }
        String path = fileUrl.replace(cosConfig.getHost(), "");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    /**
     * 从 COS 删除文件
     *
     * @param fileUrl 文件 URL
     */
    public void deleteFile(String fileUrl) {
        // 1. 校验文件 URL
        if (StringUtils.isBlank(fileUrl)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件 URL 不能为空");
        }

        // 2. 从文件 URL 中提取出文件名
        String filename = extractFilenameFromUrl(fileUrl);
        if (StringUtils.isBlank(filename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件 URL 格式错误，无法提取文件名");
        }

        // 3. 调用 CosManager 的删除方法
        cosClient.deleteObject(cosConfig.getBucketName(), filename);
        log.info("文件删除成功, fileUrl: {}", fileUrl);
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
            log.error("file delete error, filepath = {}", file.getAbsolutePath());
        }
    }


}


