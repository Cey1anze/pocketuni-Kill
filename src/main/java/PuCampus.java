import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PuCampus {
    static final SimpleDateFormat ScheduleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final Logger logger = LogManager.getLogger(PuCampus.class);
    static String defaultSettingsFile = "src/main/resources/setting.properties";
    static String Accept = getSetting("Accept");
    static String UserAgent = getSetting("User-Agent");
    static Boolean usepass = Boolean.parseBoolean(getSetting("usepass"));
    static String email = getSetting("email");
    static String username = getSetting("username");
    static String passwd = getSetting("passwd");
    static String myLocalCookies = getSetting("myCookies");
    static long activityID = Long.parseLong((getSetting("activityID_1")));
    static long activityID_2 = Long.parseLong(getSetting("activityID_2"));
    static int THREAD_POOL_SIZE = Math.min(Integer.parseInt(getSetting("ThreadPool_Size")), 30);
    static int taskMAX = Integer.parseInt(getSetting("taskMAX"));
    static boolean ifAutoSearch = Boolean.parseBoolean(getSetting("ifAutoSearch"));
    static boolean ifSchedule = Boolean.parseBoolean(getSetting("ifSchedule"));
    static boolean useLocalCookies = Boolean.parseBoolean(getSetting("useLocalCookies"));
    static String StatusStr = null;
    static volatile String hash = null;
    static String respText = null;
    static String loggedUser = null;
    static String cookie = null;
    static boolean Activity2 = false;
    static long timestampStartTime = 0;
    static AtomicInteger CheckLogin = new AtomicInteger(0);
    private static long t1;

    /**
     * 获得配置文件
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
     * 解码base64
     * @param base64Image base64编码
     * @return 格式化后编码
     */
    public static byte[] decodeBase64Image(String base64Image) {
        // 将"data:image/png;base64,"等前缀删除
        String imageDataBytes = base64Image.substring(base64Image.indexOf(",") + 1);
        return Base64.getDecoder().decode(imageDataBytes);
    }

    /**
     * 图片写入文件
     * @param imageBytes 字节流图片
     * @param filePath 文件路径
     */
    public static void writeImageToFile(byte[] imageBytes, String filePath) {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(imageBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 构造随机boundary
     * @return boundary
     */
    public static String generateBoundary() {
        SecureRandom random = new SecureRandom();
        byte[] boundaryBytes = new byte[32];
        random.nextBytes(boundaryBytes);

        // 将生成的随机字节数组转换为Base64编码字符串
        return Base64.getEncoder().encodeToString(boundaryBytes);
    }

    /**
     * 使用账号密码或二维码自动登录
     * 获取cookie中的PhpSsid TS_LOGGED_USER
     * @throws Exception e
     */
    public static void getCookies() throws Exception {
        if (useLocalCookies) {
            cookie = myLocalCookies;
            logger.info("已经手动设置Cookies绕过自动登录");
            return;
        }
        if (usepass){
            String boundary = generateBoundary();
            // 构建请求体
            String requestBody = "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"email\"\r\n\r\n" +
                    username + email + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"type\"\r\n\r\n" +
                    "pc\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"password\"\r\n\r\n" +
                    passwd + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"usernum\"\r\n\r\n" +
                    username + "\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"sid\"\r\n\r\n\r\n" +
                    "--" + boundary + "\r\n" +
                    "Content-Disposition: form-data; name=\"school\"\r\n\r\n" +
                    email + "\r\n" +
                    "--" + boundary + "--";


            HttpResponse<String> response = Unirest.post("https://pocketuni.net/index.php?app=api&mod=Sitelist&act=login")
                    .header("Cookie", "TS_think_language=zh-CN;")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:123.0) Gecko/20100101 Firefox/123.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Accept-Language", "zh-CN,zh;q=0.8,zh-TW;q=0.7,zh-HK;q=0.5,en-US;q=0.3,en;q=0.2")
                    .header("Accept-Encoding", "gzip, deflate")
                    .header("Origin", "https://pc.pocketuni.net")
                    .header("Referer", "https://pc.pocketuni.net/")
                    .header("Sec-Fetch-Dest", "empty")
                    .header("Sec-Fetch-Mode", "cors")
                    .header("Sec-Fetch-Site", "same-site")
                    .header("Te", "trailers")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .body(requestBody)
                    .asString();

            try {
                //System.out.println(response.getHeaders());

                List<String> cookies = response.getHeaders().get("Set-Cookie");
                JSONObject jsonObject = new JSONObject(response.getBody());

                JSONObject content = jsonObject.getJSONObject("content");

                String oauthToken = content.getString("oauth_token");
                String oauthTokenSecret = content.getString("oauth_token_secret");

                String TS_LOGGED_USER = null;
                for (String cookie : cookies) {
                    if (cookie.contains("TS_LOGGED_USER")) {
                        TS_LOGGED_USER = cookie.split(";")[0].split("=")[1];
                    }
                }
                //System.out.println("TS_LOGGED_USER: " + TS_LOGGED_USER);
                //System.out.println("TS_oauth_token: " + oauthToken);
                //System.out.println("TS_oauth_token_secret: " + oauthTokenSecret);
                cookie = "TS_LOGGED_USER=" + TS_LOGGED_USER + "; TS_oauth_token=" + oauthToken + "; TS_oauth_token_secret=" + oauthTokenSecret + "; TS_think_language=zh-CN;";
            }catch (Exception ignored) {
                logger.error("登录出现异常 已结束 可能网站登录错误或发生变动 ");
                System.exit(0);
            }

        } else {
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
            String dataUrl = jsonNode.path("content").path("dataUrl").asText().replace("\\", "");

            //debug info
            //logger.info("Token值: " + token);
            //logger.info("dataurl: " + dataUrl);

            byte[] imageBytes = decodeBase64Image(dataUrl);

            String filePath = "output.png"; // 图像文件路径，这里假设是png格式
            writeImageToFile(imageBytes, filePath);
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

    }

    /**
     * 获得活动名字 非关键
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
            System.out.println("活动名：[[[ " + activityName + " ]]]");
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
    public static boolean getHashStatus(long id) throws Exception {
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
            try {
                respText = body.getElementsByClass("b").text();
                hash = body.getElementsByAttributeValue("name", "__hash__").get(0).attr("value");
                System.out.println("活动ID:" + id + "\t" + respText);
                if (respText.contains("成功") || respText.contains("已满") || respText.contains("结束")) {
                    Thread.yield();
                    System.out.println("---Done---");
                    long t2 = System.currentTimeMillis();
                    Calendar c = Calendar.getInstance();
                    //统计运算时间
                    c.setTimeInMillis(t2 - t1);
                    String msg = ("耗时: " + c.get(Calendar.MINUTE) + "分 "
                            + c.get(Calendar.SECOND) + "秒 " + c.get(Calendar.MILLISECOND) + " 微秒");
                    System.out.println(msg);
                    return true;
                }
            } catch (Exception ignored) {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    /**
     * 延时启动  验证密码和 cookies 准备启动
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
            AutoSelectActivity.autoSelect();
        }
        //输出信息
        System.out.println("-".repeat(20) + " 活动详细信息 " + "-".repeat(20) + "\n活动id:" + activityID);
        getHashStatus(activityID);
        getActivityName(activityID);
        //判断有几个活动
        if (activityID_2 != 0) {
            Activity2 = true;
        }
        if (Activity2) {
            System.out.println("\n活动id:" + activityID_2);
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

    /**
     * 创建线程池
     */
    private static ExecutorService createThreadPool() {
        ThreadFactory namedThreadFactory = new ThreadFactoryBuilder().build();
        return Executors.newFixedThreadPool(THREAD_POOL_SIZE, namedThreadFactory);
    }

    /**
     * 活动报名及状态检查任务逻辑
     * @param activityID 活动id
     */
    private static void executeTask(long activityID) {
        try {
            tryOnce(activityID);
        } catch (Exception ignored) {
        }
    }

    public static void main(String[] args) throws Exception {
        EnvironmentChecker.checkJava();

        try {
            String latestVersion = GetHwid.getLatestVersion();
            String currentVersion = "2.1.0";
            // 检查新版本
            if (latestVersion != null && latestVersion.compareTo(currentVersion) > 0) {
                logger.warn("新版本可用: " + latestVersion + ", 请尽快更新到新版本.");
                logger.warn("最新版本下载地址: https://gitee.com/wxdxyyds/pocketuni-Kill/releases");
            } else {
                logger.info("已经是最新版本: " + currentVersion);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        syncWindowsTime();
        logger.info("线程池大小: " + THREAD_POOL_SIZE + "\t\t任务数: " + taskMAX);
        Unirest.setTimeouts(2000, 2000);
        validation();

        // 不同id不同线程池
        ExecutorService executor1 = createThreadPool();
        ExecutorService executor2 = createThreadPool();

        t1 = System.currentTimeMillis();

        // 创建任务
        for (int i = 0; i < taskMAX; i++) {
            if (!executor1.isShutdown()) {
                executor1.execute(() -> executeTask(activityID));
                if (getHashStatus(activityID)) {
                    executor1.shutdownNow();
                }
            }
            if (Activity2 && !executor2.isShutdown()) {
                executor2.execute(() -> executeTask(activityID_2));
                if (getHashStatus(activityID_2)) {
                    executor2.shutdownNow();
                }
            }
        }
    }
}
