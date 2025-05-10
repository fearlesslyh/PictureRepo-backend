package com.lyh.picturerepo.infrastructure.api.imagesearch;

import com.lyh.picturerepo.infrastructure.api.imagesearch.model.ImageSearchResult;
import com.lyh.picturerepo.infrastructure.api.imagesearch.sub.GetImageFirstUrlApi;
import com.lyh.picturerepo.infrastructure.api.imagesearch.sub.GetImageListApi;
import com.lyh.picturerepo.infrastructure.api.imagesearch.sub.GetImagePageUrlApi;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/5/1 0:21
 */

/**
 * 使用门面模式：通过提供一个统一的接口来简化多个接口的调用，使得客户端不需要关注内部的具体实现。
 */
@Slf4j
public class ImageSearchApiFacade {

    public static List<ImageSearchResult> searchImage(String imageUrl) {
        // 通过调用GetImageFirstUrlApi和GetImageListApi，GetImagePageUrlApi来获取图片列表和图片信息
        String imagePageUrl = GetImagePageUrlApi.getImagePageUrl(imageUrl);
        String imageFirstUrl = GetImageFirstUrlApi.getImageFirstUrl(imagePageUrl);
        List<ImageSearchResult> imageSearchResultList = GetImageListApi.getImageList(imageFirstUrl);
        return imageSearchResultList;
    }

    public static void main(String[] args) {
        // 用于测试以图搜图的功能
        String url="https://i2.hdslb.com/bfs/archive/c8fd97a40bf79f03e7b76cbc87236f612caef7b2.png";
        List<ImageSearchResult> imageSearchResultList = searchImage(url);
        System.out.println(imageSearchResultList);
    }
}
