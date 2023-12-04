import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class EnvironmentChecker {
    private static final Logger logger = LogManager.getLogger(EnvironmentChecker.class);
    /**
     * 检查Java环境是否大于指定版本
     * @param targetVersion 目标Java版本，例如 "17"
     * @return 如果Java环境大于目标版本，返回 true；否则返回 false
     */
    public static boolean isJavaVersionGreaterThan(String targetVersion) {
        try {
            int target = Integer.parseInt(targetVersion);
            String javaVersion = System.getProperty("java.version").split("\\.")[0];
            int current = Integer.parseInt(javaVersion);

            return current >= target;
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            // 处理版本号解析异常
            return false;
        }
    }

    public static void checkJava() {
        String targetJavaVersion = "17";

        // 检查Java环境是否大于目标版本
        if (isJavaVersionGreaterThan(targetJavaVersion)) {
            logger.info("Java环境正常");
        } else {
            logger.error("Java环境低于运行所需环境。请安装JDK17+");
            System.exit(0);
        }
    }
}
