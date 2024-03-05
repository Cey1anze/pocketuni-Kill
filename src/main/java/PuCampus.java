import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.time.LocalTime;
import java.util.Base64;
import java.util.Calendar;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Map;

public class PuCampus {
    static final SimpleDateFormat ScheduleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = LogManager.getLogger(PuCampus.class);
    static String defaultSettingsFile = "src/main/resources/setting.properties";
    static String Accept = getSetting("Accept");
    static String UserAgent = getSetting("User-Agent");
    static String myLocalCookies = getSetting("myCookies");
    static long activityID = Long.parseLong((getSetting("activityID_1")));
    static long activityID_2 = Long.parseLong(getSetting("activityID_2"));
    static int THREAD_POOL_SIZE = Math.min(Integer.parseInt(getSetting("ThreadPool_Size")), 30);
    static int taskMAX = Integer.parseInt(getSetting("taskMAX"));
    static boolean ifAutoSearch = Boolean.parseBoolean(getSetting("ifAutoSearch"));
    static boolean ifSchedule = Boolean.parseBoolean(getSetting("ifSchedule"));
    static boolean useLocalCookies = Boolean.parseBoolean(getSetting("useLocalCookies"));
    /**
     * 提前10秒启动即可
     * 默认输出"报名未开始"
     */
    static String StatusStr = null;
    static volatile String hash = null;
    static String respText = null;
    static String loggedUser = null;
    static String cookie = null;
    static boolean Activity2 = false;
    static long timestampStartTime = 0;
    static AtomicInteger ai = new AtomicInteger(0);
    static AtomicInteger CheckLogin = new AtomicInteger(0);

    /**
     * 获得配置文件
     *
     * @param keyWord 配置项
     * @return 配置项
     */
    public static String getSetting(String keyWord) {
        Properties prop = new Properties();
        String value = "";
        InputStream in1 = null;
        InputStream in2 = null;
        //尝试获取绝对文件路径
        String jarWholePath = PuCampus.class.getProtectionDomain().getCodeSource().getLocation().getFile();
        jarWholePath = java.net.URLDecoder.decode(jarWholePath, StandardCharsets.UTF_8);
        String jarPath = new File(jarWholePath).getParentFile().getAbsolutePath();
        //找到配置文件的相对路径

        String os = System.getProperty("os.name");
        if (os.toLowerCase().startsWith("win")) {
            jarPath = jarPath + "\\setting.properties";
        } else {
            jarPath = jarPath + "/setting.properties";
            //系统适配
        }
        //判断target目录下是否有配置文件
        if (!new File(jarPath).exists()) {
            jarPath = defaultSettingsFile;
            if (!new File(jarPath).exists()) {
                System.out.println("没找到配置文件" + jarPath);
                System.exit(0);
            }
        }
        try {
            //jarPath 优先使用同路径下jarWholePath,其次是defaultSettingsFile,找不到就退出
            in2 = new FileInputStream(jarPath);
            prop.load(in2);
            value = prop.getProperty(keyWord);
            if (value == null) System.out.println("找不到该配置项" + keyWord);
        } catch (IOException IOException) {
            System.out.println(IOException);
            System.out.println("找不到该配置项" + keyWord);
            System.exit(0);
        }
        return value;
    }

    /**
     * 同步系统时间
     * 时间服务器：ntp.aliyun.com
     */
    public static void syncWindowsTime() {
        String timeServer = "ntp.aliyun.com";
        try {
            ProcessBuilder startServiceBuilder = new ProcessBuilder("net", "start", "w32time");
            Process startServiceProcess = startServiceBuilder.start();
            int startServiceExitCode = startServiceProcess.waitFor();

            if (startServiceExitCode == 0) {
                logger.info("时间服务已启动");
            }
            // 配置时间服务器
            ProcessBuilder configureBuilder = new ProcessBuilder(
                    "w32tm", "/config", "/manualpeerlist:" + timeServer, "/syncfromflags:manual", "/reliable:YES", "/update"
            );
            Process configureProcess = configureBuilder.start();
            int configureExitCode = configureProcess.waitFor();

            if (configureExitCode == 0) {
                logger.info("时间服务器配置成功");
                // 同步时间
            } else {
                logger.warn("时间服务器配置失败");
            }
            //同步时间
            ProcessBuilder syncBuilder = new ProcessBuilder("w32tm", "/resync");
            Process syncProcess = syncBuilder.start();
            int syncExitCode = syncProcess.waitFor();

            if (syncExitCode == 0) {
                logger.info("时间同步成功");
            } else {
                logger.warn("时间同步失败");
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * 扫码登陆，二维码文件保存在运行目录
     * 获取cookie
     */
    public static void QRTime() {
        int sleepTime = 20; // 睡眠时间（秒）

        try {
            for (int i = sleepTime; i > 0; i--) {
                logger.info("剩余扫码登陆时间：" + i + " 秒");
                Thread.sleep(1000); // 暂停1秒钟
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        logger.info("扫码登陆结束，进程开始！");
    }

    /**
     * 解析cookie内容
     */
    private static String extractCookieValue(String cookieHeader) {
        if (cookieHeader != null) {
            String regex = "TS_LOGGED_USER" + "=([^;]+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(cookieHeader);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * 使用账号密码自动登录
     * 获取cookie中的PhpSsid TS_LOGGED_USER
     *
     * @throws Exception e
     */
    public static void getCookies() throws Exception {
        if (useLocalCookies) {
            cookie = myLocalCookies;
            logger.info("已经手动设置Cookies绕过自动登录");
            return;
        }
        HttpResponse<String> response1 = Unirest.post("https://pocketuni.net/index.php?app=api&mod=Sitelist&act=loginQrcode")
                .header("Host", "pocketuni.net")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Origin", "https://pc.pocketuni.net")
                .header("Referer", "https://pc.pocketuni.net/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("Te", "trailers")
                .header("Connection", "close")
                .asString();

        int status = response1.getStatus();
        String responseBody = response1.getBody();

        //debug info
        //logger.info("二维码HTTP响应状态码: " + status);
        //logger.info("二维码HTTP响应内容:" + responseBody);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // 提取token及QRCode
        String token = jsonNode.path("content").path("token").asText();
        String dataUrl = jsonNode.path("content").path("dataUrl").asText().replace("\\", "").replace("data:image/png;base64,", "");

        //debug info
        //logger.info("Token值: " + token);
        //logger.info("dataurl: " + dataUrl);

        byte[] imageBytes = Base64.getDecoder().decode(dataUrl);

        // 创建BufferedImage对象
        ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
        BufferedImage bufferedImage = ImageIO.read(bis);

        // 保存图片到运行目录
        File outputFile = new File("output.png");
        ImageIO.write(bufferedImage, "png", outputFile);
        QRTime();
        HttpResponse<String> response2 = Unirest.post("https://pocketuni.net/index.php?app=api&mod=Sitelist&act=pollingLogin&0")
                .header("Host", "pocketuni.net")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/118.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                .header("Accept-Encoding", "gzip, deflate")
                .header("Content-Type", "multipart/form-data; boundary=---------------------------196999009720122279474182198307")
                .header("Origin", "https://pc.pocketuni.net")
                .header("Referer", "https://pc.pocketuni.net/")
                .header("Sec-Fetch-Dest", "empty")
                .header("Sec-Fetch-Mode", "cors")
                .header("Sec-Fetch-Site", "same-site")
                .header("Te", "trailers")
                .header("Connection", "close")
                .body("-----------------------------196999009720122279474182198307\r\n" +
                        "Content-Disposition: form-data; name=\"token\"\r\n\r\n" +
                        token + "\r\n" +
                        "-----------------------------196999009720122279474182198307--")
                .asString();

        int status2 = response2.getStatus();
        String responseBody2 = response2.getBody();

        JsonNode jsonNode1 = objectMapper.readTree(responseBody2);
        //提取cookie
        String TS_oauth_token = jsonNode1.path("content").path("oauth_token").asText();
        String TS_oauth_token_secret = jsonNode1.path("content").path("oauth_token_secret").asText();

        //debug info
        //logger.info("登陆界面HTTP响应状态码: " + status2);
        //logger.info("登陆界面HTTP响应内容:" + responseBody2);
        //response2.getHeaders().forEach((name, values) -> {
        //System.out.println(name + ": " + values);
        //});
        //logger.info("响应头:" + response2.getHeaders());

        // 获取Set-Cookie头
        String setCookieHeader = response2.getHeaders().getFirst("Set-Cookie");

        // 使用正则表达式提取ts_logged_user的值
        loggedUser = extractCookieValue(setCookieHeader);

        try {
            //System.out.println(decodeUni(response2.getBody()));
            if (loggedUser == null) {
                logger.error("loggedUser获取错误");
                System.exit(0);
            } else {
                loggedUser = "TS_LOGGED_USER=" + loggedUser + "; ";
            }
            if (TS_oauth_token == null || TS_oauth_token_secret == null) {
                logger.error("cookies获取错误");
                System.exit(0);
            } else {
                cookie = loggedUser + "TS_oauth_token=" + TS_oauth_token + "; TS_oauth_token_secret=" + TS_oauth_token_secret + "; TS_think_language=zh-CN";
            }
        } catch (Exception ignored) {
            logger.error("登录出现异常 已结束 可能网站登录错误或发生变动 ");
            System.exit(0);
        }
    }

    public static String autoGetActivityName() throws Exception {
        HttpResponse<String> autoresponse =
                Unirest.get("https://pocketuni.net/index.php?app=event&mod=School&act=board&titkey=%E5%85%AC%E7%9B%8A%E5%8A%B3%E5%8A%A8%E4%B9%8B%E8%AE%A1%E7%AE%97%E6%9C%BA%E5%B7%A5%E7%A8%8B%E5%AD%A6%E9%99%A2")
                        .header("Host", "pocketuni.net")
                        .header("Connection", "keep-alive")
                        .header("Cache-Control", "no-cache")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("User-Agent", UserAgent)
                        .header("Accept", Accept)
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Cookie", cookie)
                        .asString();
        return autoresponse.getBody();
    }


    public static void autoselect() throws Exception {
        String htmlContent = autoGetActivityName();
        Map<String, String> titleAndTimeMap = AutoSelectActivity.extractTitleAndTime(htmlContent);

        // 打印提取的 hd_c_left_title 和 hd_c_left_time 对
        logger.debug("ActivityID and Time:");
        for (Map.Entry<String, String> entry : titleAndTimeMap.entrySet()) {
            logger.debug("ID: " + entry.getKey() + ", Start Time: " + entry.getValue());
        }
        AutoSelectActivity.compareDates(titleAndTimeMap);
        activityID = AutoSelectActivity.activityid;
        activityID_2 = AutoSelectActivity.activityid2;

        timestampStartTime = AutoSelectActivity.timestampStartTime;
        logger.debug("Selected id:" + activityID);
        logger.debug("Selected id2:" + activityID_2);
    }

    /**
     * 获得活动名字 非关键
     *
     * @param id 活动id
     * @throws Exception e
     */
    public static void getActivityName(long id) throws Exception {
        HttpResponse<String> response3 =
                Unirest.get("https://pocketuni.net/index.php?app=event&mod=Front&act=index&id=" + id)
                        .header("Host", "pocketuni.net")
                        .header("Connection", "keep-alive")
                        .header("Cache-Control", "no-cache")
                        .header("Upgrade-Insecure-Requests", "1")
                        .header("User-Agent", UserAgent)
                        .header("Accept", Accept)
                        .header("Accept-Encoding", "gzip, deflate")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .header("Cookie", cookie)
                        .asString();
        Element body = Jsoup.parseBodyFragment(response3.getBody());
        try {
            String activityName = body.getElementsByClass("b").text();
            System.out.println("活动名[[[ " + activityName + " ]]]");
            String activityInfo =
                    body.getElementsByClass("content_hd_c").text();
            try {
                activityInfo = activityInfo.split("报名起止[：|:]")[1].split("联系")[0];
            } catch (Exception ignored) {
            }
            System.out.println(activityInfo);
        } catch (Exception ignored) {
            logger.error("无法获得活动信息!");
            System.exit(0);
        }
    }

    /**
     * 获取hash值和当前状态
     * 同时可以判断是否登录成功
     *
     * @throws Exception e
     */
    public static void getHashStatus(long id) throws Exception {
        Element body = null;
        try {
            HttpResponse<String> response4 =
                    Unirest.post("https://pocketuni.net/index.php?app=event&mod=Front&act=join&id=" + id)
                            .header("Host", "pocketuni.net")
                            .header("Connection", "keep-alive")
                            .header("sec-ch-ua", "\"Google Chrome\";v=\"89\", \"Chromium\";v=\"89\", \";Not A Brand\";v=\"99\"")
                            .header("sec-ch-ua-mobile", "?0")
                            .header("Upgrade-Insecure-Requests", "1")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.114 Safari/537.36")
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
                            .header("Sec-Fetch-Site", "same-origin")
                            .header("Sec-Fetch-Mode", "navigate")
                            .header("Sec-Fetch-User", "?1")
                            .header("Sec-Fetch-Dest", "document")
                            .header("Referer", "https://pocketuni.net/index.php?app=event&mod=Front&act=board&cat=all")
                            .header("Accept-Encoding", "gzip, deflate, br")
                            .header("Accept-Language", "zh-CN,zh;q=0.9")
                            .header("Cookie", cookie)
                            .asString();
            body = Jsoup.parseBodyFragment(response4.getBody());
        } catch (Exception e) {
            logger.error("网页有改动,如可以正常报名成功就忽略");
            return;
        }
        if (CheckLogin.get() > 0) {
            try {
                respText = body.getElementsByClass("b").text();
                hash = body.getElementsByAttributeValue("name", "__hash__").get(0).attr("value");
                System.out.println("活动ID:" + id + "\t" + respText);
                CheckLogin.incrementAndGet();
                if (CheckLogin.get() > 2 && respText.contains("成功")) {
                    Thread.yield();
                    System.exit(0);
                }
                return;
            } catch (Exception ignored) {
                return;
            }
        }
        //初次使用此函数需要验证登录
        try {
            respText = body.text();
            //检查HTML段
            if (respText.contains("活动不存在")) {
                System.out.println("活动不存在 ");
                System.exit(0);
            }
            if (respText.contains("苏州天宫")) {
                System.out.println(respText.split("苏州天宫")[0]);
            } else System.out.println(respText);
            hash = body.getElementsByAttributeValue("name", "__hash__").get(0).attr("value");
            StatusStr = body.getElementsByClass("b").text();
            if (!StatusStr.isEmpty()) {
                System.out.println(StatusStr);
            }
            CheckLogin.incrementAndGet();
        } catch (Exception ignored) {
            System.out.println("登录失败");
            if (useLocalCookies) {
                System.out.println("手动输入了错误或过时的Cookies" + cookie);
            } else {
                System.out.println("账号密码或学校代码错误");
            }
            System.exit(0);
        }
    }

    /**
     * 延时启动  验证密码和 cookies 准备启动
     *
     * @throws Exception e
     */
    public static void validation() throws Exception {
        int delay = GetHwid.hwid();
        //登录,获得cookies
        getCookies();
        logger.info(cookie);
        logger.info("登录成功 准备启动");
        timestampStartTime = ScheduleFormat.parse(getSetting("StartTime")).getTime();
        if (ifAutoSearch) {
            autoselect();
        }
        //输出信息
        System.out.println("-".repeat(20) + " 活动详细信息 " + "-".repeat(20) + "\n第一个活动id:" + activityID);
        getHashStatus(activityID);
        getActivityName(activityID);
        //判断有几个活动
        if (activityID_2 != 0) {
            Activity2 = true;
        }
        if (Activity2) {
            System.out.println("\n第二个活动id:" + activityID_2);
            getHashStatus(activityID_2);
            getActivityName(activityID_2);
        }
        //准备暂停
        if (ifSchedule) {
            try {
                long timestampSNowTime = System.currentTimeMillis();
                long sleepTime = timestampStartTime - timestampSNowTime;
                if (sleepTime > 0) {
                    System.out.println("准备运行, 本进程现在休眠  " + sleepTime / 3600000 + "时"
                            + (sleepTime % 3600000) / 60000 + "分" + sleepTime % 60000 / 1000 + "秒...");
                    Thread.sleep(sleepTime + delay);
                    //-------------------------------------------//
                    System.out.println("暂停结束 现在开始");
                }
            } catch (Exception e) {
                System.out.println("定时时间格式输入错误");
            }
        }
        System.out.println("--------------------START--------------------\n");
    }

    /**
     * 尝试并发请求
     */
    public static void tryOnce(long id) {
        try {
            Unirest.post("https://pocketuni.net/index.php?app=event&mod=Front&act=doAddUser&id=" + id)
                    .header("Host", "pocketuni.net")
                    .header("Connection", "keep-alive")
                    .header("Cache-Control", "private")
                    .header("Origin", "https://pocketuni.net")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("User-Agent", UserAgent)
                    .header("Accept", Accept)
                    .header("Referer", "https://pocketuni.net/index.php?app=event&mod=Front&act=join&id=" + id)
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Cookie", cookie)
                    .body("__hash__=" + hash)
                    .asString();
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        EnvironmentChecker.checkJava();
        try {
            String latestVersion = GetHwid.getLatestVersion();
            String currentVersion = "2.0.1";
            //检查新版本
            if (latestVersion != null && latestVersion.compareTo(currentVersion) > 0) {
                logger.warn("发现新版本：" + latestVersion + " ,请尽快更新最新版本");
            } else {
                logger.info("当前已是最新版本：" + currentVersion);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        syncWindowsTime();
        logger.info("并发线程数:" + THREAD_POOL_SIZE +
                "\t\t尝试次数:" + taskMAX);
        Unirest.setTimeouts(2000, 2000);
        validation();
        // 创建线程池，其中任务队列需要结合实际情况设置合理的容量
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(THREAD_POOL_SIZE,
                THREAD_POOL_SIZE + 1,
                0L,
                TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(taskMAX),
                namedThreadFactory,
                new ThreadPoolExecutor.AbortPolicy());
        long t1 = System.currentTimeMillis();
        //重设响应超时毫秒 超时的请求直接放弃
        Unirest.setTimeouts(800, 800);
        AtomicInteger currentIteration = new AtomicInteger(0);
        // 新建 taskMAX 个任务，每个任务是打印当前线程名称
        for (int i = 0; i < taskMAX; i++) {
            executor.execute(() -> {
                try {
                    tryOnce(activityID);
                    if (Activity2) {
                        tryOnce(activityID_2);
                    }
                    int checkFrequency = 2;
                    if (currentIteration.incrementAndGet() % checkFrequency == 0) {
                        if (!Activity2) {
                            getHashStatus(activityID);
                        } else {
                            getHashStatus(activityID);
                            getHashStatus(activityID_2);
                        }
                        currentIteration.set(0);
                    }
                    //原子计数器自增
                    ai.incrementAndGet();
                    System.out.println("当前时间:" + LocalTime.now());
                } catch (Exception ignored) {
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(1000L, TimeUnit.SECONDS);
        System.out.println("---Done---");
        long t2 = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        //统计运算时间
        c.setTimeInMillis(t2 - t1);
        System.out.println("耗时: " + c.get(Calendar.MINUTE) + "分 "
                + c.get(Calendar.SECOND) + "秒 " + c.get(Calendar.MILLISECOND) + " 微秒");
    }
}
