package com.lyh.picturerepo.domain.space.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.lyh.picturerepo.domain.user.entity.User;
import com.lyh.picturerepo.interfaces.dto.space.analyse.*;
import com.lyh.picturerepo.interfaces.vo.space.analyse.*;
import com.lyh.picturerepo.domain.space.entity.Space;

import java.util.List;

/**
 * @author 李鱼皮
 * @createDate 2024-12-18 19:53:34
 */
public interface SpaceAnalyzeDomainService extends IService<Space> {

    /**
     * 获取空间使用情况分析
     *
     * @param spaceUsageAnalyzeRequest
     * @param loginUser
     * @return
     */
    SpaceUsageResponse getSpaceUsageAnalyze(SpaceUsageAnalyse spaceUsageAnalyzeRequest, User loginUser);

    /**
     * 获取空间图片分类分析
     *
     * @param spaceCategoryAnalyse
     * @param loginUser
     * @return
     */
    List<SpaceCategoryResponse> getSpaceCategoryAnalyze(SpaceCategoryAnalyse spaceCategoryAnalyse, User loginUser);

    /**
     * 获取空间图片标签分析
     *
     * @param    spaceTagAnalyse
     * @param loginUser
     * @return
     */
    List<SpaceTagResponse> getSpaceTagAnalyze(SpaceTagAnalyse spaceTagAnalyse, User loginUser);

    /**
     * 获取空间图片大小分析
     *
     * @param spaceSizeAnalyse
     * @param loginUser
     * @return
     */
    List<SpaceSizeResponse> getSpaceSizeAnalyze(SpaceSizeAnalyse spaceSizeAnalyse, User loginUser);

    /**
     * 获取空间用户上传行为分析
     *
     * @param spaceUserAnalyse
     * @param loginUser
     * @return
     */
    List<SpaceUserResponse> getSpaceUserAnalyze(SpaceUserAnalyse spaceUserAnalyse, User loginUser);

    /**
     * 空间使用排行分析（仅管理员）
     *
     * @param
     * @param loginUser
     * @return
     */
    List<Space> getSpaceRankAnalyze(SpaceRankAnalyse spaceRankAnalyse, User loginUser);
}
