package com.lyh.picturerepobackend.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.config.CosConfig;
import com.lyh.picturerepobackend.constant.UserConstant;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.manager.CosManager;
import com.lyh.picturerepobackend.manager.FileManager;
import com.lyh.picturerepobackend.model.dto.picture.PictureUpload;
import com.lyh.picturerepobackend.model.entity.User;
import com.lyh.picturerepobackend.model.vo.PictureVO;
import com.lyh.picturerepobackend.service.PictureService;
import com.lyh.picturerepobackend.service.UserService;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.ObjectListing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件上传 Controller
 *
 * @author <a href="https://github.com/fearlesslyh">梁懿豪</a>
 * @version 1.0
 * @date 2025/4/14 17:18
 */
@RequestMapping("/file")
@RestController
@Slf4j
public class FileController {

    @Resource
    private CosManager cosManager;
    @Resource
    private CosConfig cosConfig;

    @Resource
    private COSClient cosClient;

    @Resource
    private UserService userService;

    @Resource
    private PictureService pictureService;

    @Resource
    private FileManager fileManager;

    /**
     * 文件上传
     *
     * @param multipartFile 文件
     * @return 文件的访问 URL
     */
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE) // 只有管理员角色才能上传文件
    @PostMapping("/test/upload")

    public BaseResponse<String> uploadFile(@RequestPart MultipartFile multipartFile) {

        // 1. 校验文件是否为空
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "上传文件为空");
        }

        // 2. 获取文件名
        String originalFilename = multipartFile.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名为空");
        }

        // 3. 生成新的文件名，防止重名
        String newFilename = UUID.randomUUID().toString() + "-" + originalFilename;

        // 4. 获取文件输入流
        InputStream inputStream;
        try {
            inputStream = multipartFile.getInputStream();
        } catch (IOException e) {
            log.error("文件上传失败，获取文件输入流异常: {}", e.getMessage());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "文件上传失败，请稍后重试");
        }

        // 5. 上传文件到 COS
        String fileUrl = cosManager.uploadFile(inputStream, newFilename);

        // 6. 返回文件 URL
        return ResultUtils.success(fileUrl);
    }


    /**
     * 文件下载
     *
     * @param filename 文件名
     * @param response HttpServletResponse
     */
    @PostMapping("/download")
    public void downloadFile(String filename, HttpServletResponse response) {
        if (StringUtils.isBlank(filename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        cosManager.downloadFile(filename, response);
    }

    /**
     * 删除文件
     *
     * @param filename 文件名
     * @return 是否删除成功
     */
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE) // 只有管理员角色才能删除文件
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteFile(String filename) {
        if (StringUtils.isBlank(filename)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件名不能为空");
        }
        fileManager.deleteFile(filename);
        return ResultUtils.success(true);
    }

    /**
     * 查询文件列表
     *
     * @param prefix 前缀，用于过滤文件
     * @return 包含文件 URL 的列表
     */
    @GetMapping("/list")
    public BaseResponse<List<String>> listFiles(@RequestParam(required = false) String prefix) {
        List<String> fileUrls = cosManager.listObjects(prefix);
        return ResultUtils.success(fileUrls);
    }

    @PostMapping("/upload")
    public BaseResponse<PictureVO> uploadPicture(@RequestPart("file") MultipartFile file, PictureUpload pictureUpload, HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);

        // 2.  打印日志，方便调试
        log.info("上传图片请求，图片信息：{}，用户信息：{}", pictureUpload, loginUser);

        // 3.  调用 service 方法上传图片
        PictureVO pictureVO = pictureService.uploadPicture(file, pictureUpload, loginUser);

        // 4.  返回成功结果
        return ResultUtils.success(pictureVO);
    }
}
