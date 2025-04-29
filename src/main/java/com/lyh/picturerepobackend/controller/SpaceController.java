package com.lyh.picturerepobackend.controller;

import com.lyh.picturerepobackend.annotation.AuthorityCheck;
import com.lyh.picturerepobackend.common.BaseResponse;
import com.lyh.picturerepobackend.common.ResultUtils;
import com.lyh.picturerepobackend.constant.UserConstant;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import com.lyh.picturerepobackend.exception.ThrowUtils;
import com.lyh.picturerepobackend.model.dto.space.SpaceLevel;
import com.lyh.picturerepobackend.model.dto.space.SpaceUpdate;
import com.lyh.picturerepobackend.model.entity.Space;
import com.lyh.picturerepobackend.model.enums.SpaceLevelEnum;
import com.lyh.picturerepobackend.service.SpaceService;
import com.lyh.picturerepobackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/27 22:18
 */
@Slf4j
@RequestMapping("/space")
@RestController
public class SpaceController {

    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;


    // 更新步骤：1.dto转换 2.数据校验 3.填充数据 4.操作数据库更新
    @PostMapping("/update")
    @AuthorityCheck(mustHaveRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdate spaceUpdate) {
        if (spaceUpdate == null || spaceUpdate.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求的参数为空或id为空");
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdate, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判断是否存在
        Long id = spaceUpdate.getId();
        Space serviceById = spaceService.getById(id);
        ThrowUtils.throwIf(serviceById == null, ErrorCode.NOT_FOUND_ERROR, "不存在该空间");
        // 更新
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新失败");
        return ResultUtils.success(true);
    }

    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()
                ))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }
}
