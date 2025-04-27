package com.lyh.picturerepobackend.manager;

import cn.hutool.core.io.FileUtil;
import com.lyh.picturerepobackend.config.CosConfig;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/14 17:16
 */
@Component
@Slf4j
public class CosManager {
    @Resource
    private CosConfig cosConfig;

    @Resource
    private COSClient cosClient;


    /**
     * 上传文件到 COS
     *
     * @param inputStream 文件输入流
     * @param filename    文件名
     * @return 文件在 COS 上的访问 URL
     */
    /**
     * 上传文件到 COS
     *
     * @param inputStream 文件输入流
     * @param filename    文件名
     * @return 文件在 COS 上的访问 URL
     */
    public String uploadFile(InputStream inputStream, String filename) {
        // 1. 创建上传 Object 的 Metadata
        ObjectMetadata objectMetadata = new ObjectMetadata();
        try {
            objectMetadata.setContentLength(inputStream.available());
        } catch (IOException e) {
            log.error("获取文件长度失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败，无法获取文件长度");
        }

        // 2.  上传文件到 COS
        PutObjectRequest putObjectRequest =
                new PutObjectRequest(cosConfig.getBucketName(), filename, inputStream, objectMetadata);
        try {
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
            log.info("文件上传成功, ETag: {}", putObjectResult.getETag());
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败，请稍后重试");
        }

        // 3. 拼接文件访问 URL
        //  String fileUrl = cosConfig.getHost() + "/" + filename; //  修改前：包含完整的存储桶URL
        return "/" + filename;
    }


    /**
     * 下载文件
     *
     * @param filename 文件名
     * @param response HttpServletResponse
     */
    public void downloadFile(String filename, HttpServletResponse response) {
        try {
            log.info("开始下载文件，文件名：{}", filename); // 添加日志

            // 1. 构造GetObjectRequest
            GetObjectRequest getObjectRequest = new GetObjectRequest(cosConfig.getBucketName(), filename);
            log.info("GetObjectRequest: {}", getObjectRequest); // 打印请求信息

            // 2. 获取文件对象
            COSObject cosObject = cosClient.getObject(getObjectRequest);
            log.info("COSObject: {}", cosObject); // 打印 COSObject
            InputStream inputStream = cosObject.getObjectContent();
            log.info("InputStream 获取成功"); // 提示输入流获取成功

            // 3. 设置响应头
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(filename, "UTF-8"));
            log.info("响应头设置完成"); // 提示响应头设置完成

            // 4. 将文件内容写入到响应体
            OutputStream outputStream = response.getOutputStream();
            log.info("OutputStream 获取成功");  // 提示输出流获取成功
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
            log.info("文件内容写入完成");  // 提示文件内容写入完成

            // 5. 关闭流
            inputStream.close();
            outputStream.close();
            cosObject.close();

            log.info("文件下载成功，文件名：{}", filename);

        } catch (IOException e) {
            log.error("文件下载失败，文件名：{}，错误信息：{}", filename, e.getMessage(), e); // 打印完整的异常堆栈
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件下载失败，请稍后重试");
        }
    }


    /**
     * 从 COS 删除文件
     *
     * @param key 文件名
     */
    public void deleteFile(String key) throws CosClientException {
        cosClient.deleteObject(cosConfig.getBucketName(), key);
        log.info("文件删除成功, filename: {}", key);
    }

    /**
     * 查询对象列表
     *
     * @param prefix 前缀，用于过滤文件
     * @return 包含文件 URL 的列表
     */
    public List<String> listObjects(String prefix) {
        List<String> fileUrls = new ArrayList<>();
        ObjectListing objectListing = null;
        String nextMarker = null; // 初始nextMarker为空
        do {
            if (null == objectListing) {
                // 首次拉取，prefix为空时拉取整个存储桶
                objectListing = cosClient.listObjects(cosConfig.getBucketName(), prefix);
            } else {
                //  从上次拉取的结果中继续拉取, 使用marker
                objectListing = cosClient.listObjects(new ListObjectsRequest().withPrefix(prefix).withMarker(nextMarker));
            }
            List<COSObjectSummary> objectSummaries = objectListing.getObjectSummaries();
            for (COSObjectSummary objectSummary : objectSummaries) {
                // 拼接文件URL
                String fileUrl = cosConfig.getHost() + "/" + objectSummary.getKey();
                fileUrls.add(fileUrl);
            }
            nextMarker = objectListing.getNextMarker();
        } while (objectListing.isTruncated()); // 判断是否还有更多对象
        return fileUrls;
    }

    /**
     * 上传对象（附带图片信息）
     *
     * @param key  唯一键
     * @param file 文件
     * @return PutObjectResult  包含上传结果
     */
    public PutObjectResult putPictureObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosConfig.getBucketName(), key,
                file);
        // 对图片进行处理（获取基本信息也被视作为一种处理）
        PicOperations picOperations = new PicOperations();
        // 1 表示返回原图信息
        picOperations.setIsPicInfo(1);
        List<PicOperations.Rule> rules = new ArrayList<>();
        // 图片压缩（转成 webp 格式）
        String webpKey = FileUtil.mainName(key) + ".webp";
        PicOperations.Rule compressRule = new PicOperations.Rule();
        compressRule.setRule("imageMogr2/format/webp");
        compressRule.setBucket(cosConfig.getBucketName());
        compressRule.setFileId(webpKey);
        rules.add(compressRule);
        // 缩略图处理, 如果文件大于 20KB，则生成缩略图
        if (file.length() > 20 * 1024) {
            PicOperations.Rule thumbnailRule = new PicOperations.Rule();
            thumbnailRule.setBucket(cosConfig.getBucketName());
            String thumbnailKey = FileUtil.mainName(key) + "_thumbnail." + FileUtil.getSuffix(key);
            thumbnailRule.setFileId(thumbnailKey);
            // 缩放规则 /thumbnail/<Width>x<Height>>（如果大于原图宽高，则不处理）
            thumbnailRule.setRule(String.format("imageMogr2/thumbnail/%sx%s>", 128, 128));
            rules.add(thumbnailRule);
        }
        // 构造处理参数
        picOperations.setRules(rules);
        putObjectRequest.setPicOperations(picOperations);
        return cosClient.putObject(putObjectRequest);
    }
}
