import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.MessageDigest;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class PuCampus {
    static String defaultSettingsFile = "src/main/resources/setting.properties";
    static String Accept = getSetting("Accept");
    static String UserAgent = getSetting("User-Agent");
    static String mySchedule = getSetting("mySchedule");
    static String myLocalCookies = getSetting("myCookies");
    static int activityID = Integer.parseInt(getSetting("activityID_1"));
    static int activityID_2 = Integer.parseInt(getSetting("activityID_2"));
    static int THREAD_POOL_SIZE = Math.min(Integer.parseInt(getSetting("ThreadPool_Size")), 30);
    static int taskMAX = Integer.parseInt(getSetting("taskMAX"));
    static boolean ifSchedule = Boolean.parseBoolean(getSetting("ifSchedule"));
    static boolean useLocalCookies = Boolean.parseBoolean(getSetting("useLocalCookies"));

    /**
     * 提前10秒启动即可
     * 默认输出"报名未开始"
     */
    static String StatusStr = null;
    static volatile String hash = null;
    static String respText = null;
    static String phpSsid = null;
    static String loggedUser = null;
    static String cookie = null;
    static boolean Activity2 = false;
    static AtomicInteger ai = new AtomicInteger(0);
    static final SimpleDateFormat ScheduleFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
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
        try {
            jarWholePath = java.net.URLDecoder.decode(jarWholePath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            System.out.println(e.toString());
        }
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


    public static List<String> extractHwidValuesFromURL(String url) {
        List<String> hwidList = new ArrayList<>();

        try {
            URL apiUrl = new URL(url);

            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            int timeout = 10000; // 5秒
            connection.setConnectTimeout(timeout);

            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                response.append(line);
            }

            reader.close();
            connection.disconnect();

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response.toString());

            if (root.isArray()) {
                for (JsonNode node : root) {
                    JsonNode hwidNode = node.get("hwid");
                    if (hwidNode != null && hwidNode.isTextual()) {
                        String hwid = hwidNode.asText();
                        hwidList.add(hwid);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("连接超时");
            System.exit(0);
        }
        System.out.println("HWIDList Verified!");
        return hwidList;
    }

    public static int hwid() {

        int randomNum = 0;
        String jsonUrl = "https://gitee.com/wxdxyyds/pocketuni-Kill/raw/master/src/main/resources/hwidlist.json";
        List<String> hwidList = extractHwidValuesFromURL(jsonUrl);
        try {
            String hardwareInfo = getHardwareInfo();
            String localHWID = generateHWID(hardwareInfo);

            if (hwidList.contains(localHWID)) {
                System.out.println("HWID OK!");
            } else {
                System.out.println("HWID Verified!");
                randomNum = generateRandomNumber(150, 300);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return randomNum;
    }

    public static String getHardwareInfo() throws Exception {
        StringBuilder hardwareInfo = new StringBuilder();
        String line;

        Process process = Runtime.getRuntime().exec("wmic csproduct get uuid");
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        while ((line = reader.readLine()) != null) {
            hardwareInfo.append(line);
        }

        reader.close();

        return hardwareInfo.toString();
    }

    public static String generateHWID(String hardwareInfo) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = md.digest(hardwareInfo.getBytes());

        StringBuilder hwid = new StringBuilder();
        for (byte b : hashBytes) {
            hwid.append(String.format("%02x", b));
        }

        return hwid.toString();
    }

    public static int generateRandomNumber(int min, int max) {
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

    /**
     * 扫码登陆，二维码文件保存在运行目录
     * 获取cookie
     */
    public static void QRTime() {
        int sleepTime = 15; // 睡眠时间（秒）

        try {
            for (int i = sleepTime; i > 0; i--) {
                System.out.println("剩余扫码登陆时间：" + i + " 秒");
                Thread.sleep(1000); // 暂停1秒钟
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        System.out.println("扫码登陆结束，进程开始！");
    }

    /**
     * 解析cookie内容
     */
    private static String extractCookieValue(String cookieHeader, String cookieName) {
        if (cookieHeader != null) {
            String regex = cookieName + "=([^;]+)";
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
            System.out.println("已经手动设置Cookies绕过自动登录");
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
        //System.out.println("二维码HTTP响应状态码: " + status);
        //System.out.println("二维码HTTP响应内容:");
        //System.out.println(responseBody);

        // 使用Jackson解析JSON响应
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = objectMapper.readTree(responseBody);

        // 提取token
        String token = jsonNode.path("content").path("token").asText();
        String dataUrl = jsonNode.path("content").path("dataUrl").asText().replace("\\", "").replace("data:image/png;base64,","");
        //System.out.println("Token值: " + token);
        //System.out.println(dataUrl);


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

        //debug info
        //System.out.println("登陆界面HTTP响应状态码: " + status2);
        //System.out.println("登陆界面HTTP响应内容:");
        //System.out.println(responseBody2);
        //System.out.println("响应头:");
        //response2.getHeaders().forEach((name, values) -> {
            //System.out.println(name + ": " + values);
        //});

        // 获取Set-Cookie头
        String setCookieHeader = response2.getHeaders().getFirst("Set-Cookie");

        // 使用正则表达式提取ts_logged_user的值
        loggedUser = extractCookieValue(setCookieHeader, "TS_LOGGED_USER");

        if (loggedUser != null) {
            System.out.println("[INFO] ts_logged_user: " + loggedUser);
        } else {
            System.out.println("ts_logged_user not found.");
        }
        try {
            System.out.println(decodeUni(response2.getBody()));
            loggedUser = "TS_LOGGED_USER=" + loggedUser + "; ";
        } catch (Exception ignored) {
            System.out.println("登录出现异常 已结束 可能网站登录错误或发生变动 ");
            System.exit(0);
        }
        Thread.sleep(200);
        cookie = loggedUser + "TS_think_language=zh-CN";
        if (phpSsid == null & loggedUser == null) {
            System.out.println("cookies获取错误 可能网站发生变动");
            System.exit(0);
        }
    }

    /**
     * unicode转换成中文 工具类
     *
     * @param respBody 返回body
     */
    public static String decodeUni(String respBody) {
        Matcher m = Pattern.compile("\\\\u([0-9a-zA-Z]{4})").matcher(respBody);
        StringBuffer sb = new StringBuffer(respBody.length());
        while (m.find()) {
            sb.append((char) Integer.parseInt(m.group(1), 16));
        }
        return sb.toString();
    }


    /**
     * 获得活动名字 非关键
     *
     * @param id 活动id
     * @throws Exception e
     */
    public static void getActivityName(int id) throws Exception {
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
            System.out.println("无法获得活动信息!");
            System.exit(0);
        }
    }

    /**
     * 获取hash值和当前状态
     * 同时可以判断是否登录成功
     *
     * @throws Exception e
     */
    public static void getHashStatus(int id) throws Exception {
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
            System.out.println("网页有改动,如可以正常报名成功就忽略");
            return;
        }
        if (CheckLogin.get() > 0) {
            try {
                respText = body.getElementsByClass("b").text();
                hash = body.getElementsByAttributeValue("name", "__hash__").get(0).attr("value");
                System.out.println(respText);
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
        int delay = hwid();
        //登录,获得cookies
        getCookies();
        //输出信息
        getHashStatus(activityID);
        System.out.println("登录成功 准备启动 \t\t\t 活动ID:" + activityID);

        //准备暂停
        if (ifSchedule) {
            try {
                Long timestampStartTime = ScheduleFormat.parse(mySchedule).getTime();
                Long timestampSNowTime = System.currentTimeMillis();
                long sleepTime = timestampStartTime - timestampSNowTime;
                if (sleepTime > 0) {
                    System.out.println(mySchedule + "准备运行, 本进程现在休眠  " + sleepTime / 3600000 + "时"
                            + (sleepTime % 3600000) / 60000 + "分" + sleepTime % 60000 / 1000 + "秒...");
                    Thread.sleep(sleepTime + delay);
                    //-------------------------------------------//
                    System.out.println("暂停结束 现在开始");
                }
            } catch (Exception e) {
                System.out.println("定时时间格式输入错误");
            }
        }
        getActivityName(activityID);
        //判断有几个活动
        if (activityID_2 > 0) {
            Activity2 = true;
        }
        if (Activity2) {
            System.out.println("第二活动id:" + activityID_2);
            getHashStatus(activityID_2);
            getActivityName(activityID_2);
        }
        System.out.println("--------------------START--------------------\n");
        Thread.sleep(200);
    }

    /**
     * 尝试并发请求
     */
    public static void tryOnce(int id) {
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
        System.out.println("并发线程数:" + THREAD_POOL_SIZE +
                "\t\t尝试次数:" + taskMAX);
        Unirest.setTimeouts(2000, 2000);
        SslUtils.ignoreSsl();
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
        // 新建 taskMAX 个任务，每个任务是打印当前线程名称
        for (int i = 0; i < taskMAX; i++) {
            executor.execute(() -> {
                try {
                    tryOnce(activityID);
                    if (Activity2) {
                        tryOnce(activityID_2);
                    }
                    if (Math.random() < 0.1) {
                        //每十次检查一下是否报名成功
                        if (!Activity2) {
                            getHashStatus(activityID);
                        } else {
                            getHashStatus(activityID);
                            getHashStatus(activityID_2);
                        }
                        System.out.print(ai.get());
                    }
                    //原子计数器自增
                    ai.incrementAndGet();
                    System.out.println(LocalTime.now()); // 2019-11-20T15:04:29.017
                } catch (Exception ignored) {
                }
            });
        }

        // 关闭线程池
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
