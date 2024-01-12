package com.mobaijun.oil;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.io.resource.ClassPathResource;
import cn.hutool.extra.mail.MailAccount;
import cn.hutool.extra.mail.MailUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Description: [油价解析]
 * Author: [mobaijun]
 * Date: [2023/12/2 19:09]
 * IntelliJ IDEA Version: [IntelliJ IDEA 2023.1.4]
 */
public class OilParse {

    /**
     * 日志打印
     */
    private static final Log log = LogFactory.get(OilParse.class);

    /**
     * 爬取地址 <a href="http://www.qiyoujiage.com/hubei/shiyan.shtml">...</a>
     */
    private static final String START_URL = System.getenv("OIL_PRICE_PUSH_API");

    /**
     * 汽油说明
     */
    private static final String DE_SCRIPT_GASOLINE_MODEL = """
            92汽油的平均密度为0.725kg/L，一升92号汽油为0.725千克;
            95号汽油的密度为0.737g/ml，一升95号汽油为0.737千克;
            0号柴油的密度在0.8400--0.8600g/cm⒊之间，一升0#柴油大约是0.84千克;""";

    private static final String OIL_PRICE_ADJUST = "油价调整";

    private static final String SMTP_HOST = "smtp.163.com";
    private static final int SMTP_PORT = 465;

    /**
     * 邮箱
     */
    private static final String USER_EMAIL = System.getenv("OIL_PRICE_PUSH_USER_EMAIL");

    /**
     * 安全码
     */
    private static final String USER_PASSWORD = System.getenv("OIL_PRICE_PUSH_USER_PASSWORD");

    /**
     * 邮件发送
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        String oilPriceEmail = generateOilPriceEmail(parseOilData());
        MailAccount account = createMailAccountTemplate();
        sendMail(account, oilPriceEmail);
    }

    /**
     * 解析油价数据的方法。
     *
     * @return 包含解析结果的 Map。
     */
    private static Map<String, String> parseOilData() {
        Map<String, String> oilMap = new LinkedHashMap<>();

        try {
            // 获取网页内容
            Document document = Jsoup.connect(OilParse.START_URL).timeout(3000).get();

            // 提取油价调整信息并放入 Map
            String oilPriceAdjustment = extractOilPriceAdjustment(document);
            oilMap.put(OIL_PRICE_ADJUST, oilPriceAdjustment);

            // 将其余数据解析并放入 Map
            oilMap.putAll(splitData(document));
        } catch (IOException e) {
            // 打印错误消息和异常堆栈跟踪
            log.error("HTML parsing failed, please try again! error message:{}", e.getMessage());
        }

        return oilMap;
    }

    /**
     * 从文档中提取油价调整信息。
     *
     * @param document Jsoup 解析后的文档对象。
     * @return 提取的油价调整信息。
     */
    private static String extractOilPriceAdjustment(Document document) {
        // 获取特定元素的文本内容
        return Objects.requireNonNull(document.selectXpath("//*[@id=\"youjiaCont\"]/div[2]")).text();
    }

    /**
     * 将油价相关数据解析为 Map。
     *
     * @param document Jsoup 解析后的文档对象。
     * @return 包含解析结果的 Map。
     */
    private static Map<String, String> splitData(Document document) {
        // 获取特定元素的文本内容
        String you = Objects.requireNonNull(document.getElementById("youjia")).text();
        // 调用另一个方法处理解析逻辑
        return splitData(you);
    }

    /**
     * 将输入的字符串解析成键值对的 Map。
     *
     * @param inputData 包含键值对的字符串。
     * @return 解析后的键值对 Map。
     */
    private static Map<String, String> splitData(String inputData) {
        Map<String, String> keyValuePairs = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile("(\\S+)\\s(\\S+)");
        Matcher matcher = pattern.matcher(inputData);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = matcher.group(2);
            keyValuePairs.put(key, value);
        }
        return keyValuePairs;
    }

    /**
     * 创建邮件账户模板。
     *
     * @return 创建的邮件账户。
     */
    private static MailAccount createMailAccountTemplate() {
        MailAccount account = new MailAccount();
        account.setHost(SMTP_HOST);
        account.setPort(SMTP_PORT);
        account.setUser(USER_EMAIL);
        account.setPass(USER_PASSWORD);
        account.setFrom(USER_EMAIL);
        account.setCharset(StandardCharsets.UTF_8);
        account.setStarttlsEnable(false);
        account.setSslEnable(true);
        return account;
    }

    /**
     * 发送邮件。
     *
     * @param account       邮件账户。
     * @param oilPriceEmail 油价邮件内容。
     */
    private static void sendMail(MailAccount account, String oilPriceEmail) {
        try {
            MailUtil.send(account, USER_EMAIL, "湖北十堰今日油价", oilPriceEmail, true);
            log.info("邮件发送成功！");
        } catch (Exception e) {
            log.error("邮件发送失败：" + e.getMessage());
        }
    }

    private static String generateOilPriceEmail(Map<String, String> oilData) {
        // 读取 Thymeleaf 模板
        String template = readTemplate();
        // 使用 Thymeleaf 引擎进行数据填充
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("title", "十堰今日油价");
        context.setVariable("subTitle", "每日即时更新 单位:元/升");
        context.setVariable("oilDeScript", DE_SCRIPT_GASOLINE_MODEL);
        context.setVariable("oilPriceAdjust", oilData.getOrDefault(OIL_PRICE_ADJUST, ""));
        oilData.remove(OIL_PRICE_ADJUST);
        context.setVariable("oilData", oilData);
        assert template != null;
        return templateEngine.process(template, context);
    }

    /**
     * 读取 Template
     *
     * @return 内容，如果读取失败返回 null。
     */
    private static String readTemplate() {
        try (InputStream inputStream = new ClassPathResource("oil_price_template.html").getStream()) {
            return IoUtil.read(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("模板文件读取失败：{}", e.getMessage());
            return null;
        }
    }
}
