package com.github.argszero.guahaocode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by shaoaq on 8/14/15.
 */
@RestController
public class Controller {
    @RequestMapping("/api/expert/{expertId}")
    JsonNode getExportInfo(@PathVariable String expertId, @RequestParam String user, @RequestParam String pwd) throws IOException, InterruptedException {
        ObjectNode objectNode = new ObjectNode(JsonNodeFactory.instance);
        RequestConfig config = null;
        CloseableHttpClient httpClient = null;
        if (System.getProperty("http.proxyHost") != null) {
            config = RequestConfig.custom().setProxy(
                    new HttpHost(System.getProperty("http.proxyHost"),
                            Integer.parseInt(System.getProperty("http.proxyPort"))
                    )
            ).build();
            if (System.getProperty("http.proxyUserName") != null) {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                        new AuthScope("proxy.asiainfo.com", 8080),
                        new UsernamePasswordCredentials(System.getProperty("http.proxyUserName"), System.getProperty("http.proxyPassword")));
                httpClient = HttpClients.custom()
                        .setDefaultCredentialsProvider(credsProvider).build();
            } else {
                httpClient = HttpClients.createDefault();
            }
        } else {
            httpClient = HttpClients.createDefault();
        }


        if (!login(user, pwd, httpClient, config)) {
            objectNode.put("success", false);
            objectNode.put("msg", "用户名或密码错误");
            return objectNode;
        }


        HttpGet get = new HttpGet("http://www.guahao.com/expert/" + expertId);
        get.setConfig(config);
        CloseableHttpResponse response = httpClient.execute(get);
        String responseString = EntityUtils.toString(response.getEntity());
        Pattern pattern = Pattern.compile("选择医院((:?[^<]*<a[^<]*</a>)*)[^<]*</div>");
        Matcher matcher = pattern.matcher(responseString);
        ArrayNode hospitals = new ArrayNode(JsonNodeFactory.instance);
        objectNode.set("hospitals", hospitals);
        while (matcher.find()) {
            String hospitalsString = matcher.group(1);
            findHospital(hospitals, hospitalsString);
        }

        pattern = Pattern.compile("选择科室((:?[^<]*<span[^>]*>(:?[^<]*<a[^<]*</a>)*[^<]*</span>)*)[^<]*</div>");
        matcher = pattern.matcher(responseString);
        while (matcher.find()) {
            String allDepts = matcher.group(1);
            findAllDepts(hospitals, allDepts, expertId, httpClient, config);
        }
        System.out.println(hospitals);
        return objectNode;
    }

    private boolean login(String user, String pwd, CloseableHttpClient httpClient, RequestConfig config) throws IOException, InterruptedException {
        Code code = new Code();
        for (int i = 0; i < 10; i++) {
            if (tryLogin(code, user, pwd, httpClient, config)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryLogin(Code code, String user, String pwd, CloseableHttpClient httpClient, RequestConfig config) throws IOException, InterruptedException {
        HttpGet get = new HttpGet("http://www.guahao.com/");
        if (System.getProperty("http.proxyHost") != null) {
            get.setConfig(config);
        }

        CloseableHttpResponse response = httpClient.execute(get);
        EntityUtils.toString(response.getEntity());

        get = new HttpGet("http://www.guahao.com/validcode/genimage/" + System.currentTimeMillis());
        if (System.getProperty("http.proxyHost") != null) {
            get.setConfig(config);
        }
        response = httpClient.execute(get);
        byte[] bytes = EntityUtils.toByteArray(response.getEntity());
        File file = File.createTempFile("guahaocode", "tmpCode");
        String guessCode;
        try {
            FileUtils.writeByteArrayToFile(file, bytes);
            guessCode = code.guess(file.toURI().toURL());
        } finally {
            file.deleteOnExit();
            file.delete();
        }


        HttpPost post = new HttpPost("http://www.guahao.com/user/login");
        if (System.getProperty("http.proxyHost") != null) {
            post.setConfig(config);
        }
        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("method", "dologin"));
        nvps.add(new BasicNameValuePair("target", ""));
        nvps.add(new BasicNameValuePair("loginId", "a"));
        nvps.add(new BasicNameValuePair("password", "b"));
        nvps.add(new BasicNameValuePair("validCode", guessCode));
        post.setEntity(new UrlEncodedFormEntity(nvps));
        response = httpClient.execute(post);
        String responseStr = EntityUtils.toString(response.getEntity());
        boolean correct = !responseStr.contains("验证码错误");
        return correct;
    }

    private Matcher findAllDepts(ArrayNode hospitals, String allDepts, String expertId, CloseableHttpClient httpClient, RequestConfig config) throws IOException {
        Pattern pattern = Pattern.compile("<span[^>]*data-hid=\"([^\"]*)\">((:?[^<]*<a[^<]*</a>)*)[^<]*</span>");
        Matcher matcher = pattern.matcher(allDepts);
        while (matcher.find()) {
            String hospitalId = matcher.group(1);
            ObjectNode hospital = null;
            for (int i = 0; i < hospitals.size(); i++) {
                hospital = (ObjectNode) hospitals.get(i);
                if (hospital.get("id").asText().equals(hospitalId)) {
                    break;
                }
            }
            findDepts(hospital, matcher.group(2), expertId, httpClient, config);
        }
        return matcher;
    }

    private void findDepts(ObjectNode hospital, String deptsString, String expertId, CloseableHttpClient httpClient, RequestConfig config) throws IOException {
        ArrayNode depts = new ArrayNode(JsonNodeFactory.instance);
        hospital.set("depts", depts);
        Pattern pattern = Pattern.compile("[^<]*<a[^<]*data-did=\"([^\"]*)\">([^<]*)</a>");
        Matcher matcher = pattern.matcher(deptsString);
        while (matcher.find()) {
            ObjectNode dept = new ObjectNode(JsonNodeFactory.instance);
            depts.add(dept);
            String deptId = matcher.group(1);
            dept.put("id", deptId);
            dept.put("name", matcher.group(2));

            HttpGet get = new HttpGet("http://www.guahao.com/expert/shiftcase/?expertId=" + expertId + "&hospDeptId=" + deptId);
            get.setConfig(config);
            CloseableHttpResponse response = httpClient.execute(get);
            String responseString = EntityUtils.toString(response.getEntity());
            ObjectMapper mapper = new ObjectMapper();
            JsonNode shiftcase = mapper.readTree(responseString);
            dept.set("shiftcase", shiftcase);
        }
    }

    private void findHospital(ArrayNode hospitals, String hospitalsString) {
        Pattern pattern = Pattern.compile("[^<]*<a[^<]*data-hid=\"([^\"]*)\">([^<]*)</a>");
        Matcher matcher = pattern.matcher(hospitalsString);
        while (matcher.find()) {
            ObjectNode hospital = new ObjectNode(JsonNodeFactory.instance);
            hospital.put("id", matcher.group(1));
            hospital.put("name", matcher.group(2));
            hospitals.add(hospital);
        }
    }

}
