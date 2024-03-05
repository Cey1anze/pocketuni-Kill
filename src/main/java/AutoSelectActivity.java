import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

public class AutoSelectActivity {

    static long activityid = 0;
    static long activityid2 = 0;
    static String selectedStartTime = null;
    static long timestampStartTime = 0;
    private static final Logger logger = LogManager.getLogger(AutoSelectActivity.class);

    /**
     * 从网页中提取活动标题和开始时间
     * @param htmlContent 网页内容
     * @return 活动标题和开始时间的 Map
     */
    public static Map<String, String> extractTitleAndTime(String htmlContent) {
        Map<String, String> titleAndTimeMap = new HashMap<>();

        Document document = Jsoup.parse(htmlContent);

        // 选择所有包含 class="hd_c_left_infor" 的 div 元素
        Elements inforElements = document.select("div.hd_c_left_infor");

        // 提取每个 hd_c_left_infor 元素下的 hd_c_left_title 和 hd_c_left_time 信息
        for (Element inforElement : inforElements) {
            // 选择包含 class="hd_c_left_title b" 的 div 元素
            Element titleElement = inforElement.selectFirst("div.hd_c_left_title.b");

            // 选择包含 class="hd_c_left_time" 的 div 元素
            Element timeElement = inforElement.selectFirst("div.hd_c_left_time");

            if (titleElement != null && timeElement != null) {
                // 提取 hd_c_left_title 元素下的 href 中的 id 字段
                Element aElement = titleElement.selectFirst("a");
                String href = aElement.attr("href");
                String titleId = extractIdFromUrl(href);

                // 提取 hd_c_left_time 元素下的活动开始时间的起始时间部分
                Element spanElement = timeElement.selectFirst("span.black");
                String eventTime = spanElement.nextSibling().toString().trim();
                String startTime = eventTime.split("至")[0].trim();

                // 将 hd_c_left_title 和 hd_c_left_time 对应起来，并存入Map中
                titleAndTimeMap.put(titleId, startTime);
            }
        }

        return titleAndTimeMap;
    }
    /**
     * 从 URL 中提取 id 参数
     * @param url URL
     * @return id 参数
     */
    public static String extractIdFromUrl(String url) {
        try {
            URI uri = new URI(url);
            String query = uri.getQuery();

            if (query != null) {
                // 将查询参数拆分为键值对
                String[] pairs = query.split("&");

                for (String pair : pairs) {
                    // 拆分键值对
                    String[] keyValue = pair.split("=");

                    // 如果键是"id"，则返回相应的值
                    if (keyValue.length == 2 && "id".equals(keyValue[0])) {
                        return keyValue[1];
                    }
                }
            }
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // 如果没有找到"id"参数，返回空字符串
        logger.error("未找到符合的活动");
        return "";
    }
    /**
     * 配置活动id及开始时间
     * @param titleAndTimeMap 从网页中提取的活动标题和开始时间的 Map
     */
    public static long compareDates(Map<String, String> titleAndTimeMap) {
        // 获取当前系统日期
        LocalDate currentDate = LocalDate.now();
        //LocalDate currentDate = LocalDate.parse("2023-12-04");

        // 遍历 titleAndTimeMap 中的日期
        for (Map.Entry<String, String> entry : titleAndTimeMap.entrySet()) {
            String titleid = entry.getKey();
            String startTime = entry.getValue();

            // 将活动开始时间的字符串转换为 LocalDate 对象
            LocalDateTime startDateTime = LocalDateTime.parse(startTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            // 判断当前系统日期和活动开始日期是否一致
            if (currentDate.isEqual(startDateTime.toLocalDate())) {
                // 一致则定义符合条件的 activity_id 和 startTime
                if (selectedStartTime == null) {
                    selectedStartTime = startTime;
                }
                if (activityid == 0) {
                    activityid = Long.parseLong(titleid);
                } else {
                    if (activityid2 == 0) {
                        activityid2 = Long.parseLong(titleid);
                    } else {
                        // 如果已经有两个符合条件的 titleid，则结束循环
                        break;
                    }
                }
            }
        }

        logger.debug("Availability of activity: " + activityid);
        logger.debug("Availability of activity2: " + activityid2);
        // 如果有符合条件的 startTime，则将其转换为毫秒数返回
        if (selectedStartTime != null) {
            LocalDateTime selectedDateTime = LocalDateTime.parse(selectedStartTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            timestampStartTime = selectedDateTime.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }else {
            logger.error("活动时间已过期");
        }

        // 如果没有符合条件的 startTime，则返回 -1
        return -1;
    }
}
