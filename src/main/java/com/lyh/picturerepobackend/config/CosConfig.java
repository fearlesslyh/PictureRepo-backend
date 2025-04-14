package com.lyh.picturerepobackend.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.BasicSessionCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author <a href=https://github.com/fearlesslyh> 梁懿豪 </a>
 * @version 1.0
 * @date 2025/4/14 17:00
 */
@Configuration
@Data
@ConfigurationProperties(prefix = "cos")
public class CosConfig {
    private String host;
    private String bucketName;
    private String secretId;
    private String secretKey;
    private String region;

    @Bean
    public COSClient cosClient() {
        // 1 初始化用户身份信息（secretId, secretKey）。
        // SECRETID 和 SECRETKEY 请登录访问管理控制台 https://console.cloud.tencent.com/cam/capi 进行查看和管理
        BasicCOSCredentials basicCOSCredentials = new BasicCOSCredentials(secretId, secretKey);
        //2 设置 bucket 的地域, COS 地域的简称请参见 https://cloud.tencent.com/document/product/436/6224
        Region newRegion = new Region(region);
        ClientConfig clientConfig = new ClientConfig(newRegion);
        // 3 生成 cos 客户端。
        return new COSClient(basicCOSCredentials, clientConfig);

    }
}
