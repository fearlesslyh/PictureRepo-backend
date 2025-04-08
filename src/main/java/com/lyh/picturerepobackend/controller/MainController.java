package com.lyh.picturerepobackend.controller;

import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/7 22:29
 */
@RestController
@RequestMapping("/") //根路径
public class MainController {
    @GetMapping("/health")
    public BaseResponse<String> health(){
        return ResultUtils.success("ok");
    }
}
