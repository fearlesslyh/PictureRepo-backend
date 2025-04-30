package com.lyh.picturerepobackend.api.imagesearch.sub;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.lyh.picturerepobackend.exception.BusinessException;
import com.lyh.picturerepobackend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 获取图片列表接口的 Api（Step 2）
 * 获得相似图片的json数据的url，需要以图搜图的结果地址页面
 */
@Slf4j
public class GetImageFirstUrlApi {

    public static String getImageFirstUrl(String resultPageUrl) {
        try {
            // 请求识图结果页
            Document document = Jsoup.connect(resultPageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .timeout(10000)
                    .get();

            Elements scripts = document.getElementsByTag("script");

            for (Element script : scripts) {
                String content = script.html();
                if (content.contains("\"firstUrl\"")) {
                    // 提取 firstUrl
                    Pattern pattern = Pattern.compile("\"firstUrl\"\\s*:\\s*\"(.*?)\"");
                    Matcher matcher = pattern.matcher(content);
                    if (matcher.find()) {
                        String firstUrl = matcher.group(1).replace("\\/", "/");
                        log.info("搜索成功，firstUrl = {}", firstUrl);
                        return firstUrl;
                    }
                }
            }

            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未找到 firstUrl");

        } catch (Exception e) {
            log.error("识图搜索解析失败", e);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "识图页面解析失败");
        }
    }

    public static void main(String[] args) {
        // 注意：这里必须是上传图片后的跳转地址（含参数）
        String resultUrl = "https://graph.baidu.com/s?card_key=&entrance=GENERAL&extUiData[isLogoShow]=1&f=all&isLogoShow=1&session_id=13959461402233416718&sign=12623eab64be3e7f9787f01746023883&tpl_from=pc";
        String firstUrl = getImageFirstUrl(resultUrl);
        System.out.println("结果页地址：" + firstUrl);
    }
}
