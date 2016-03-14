package com.github.argszero.guahaocode;

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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Code Tester.
 *
 * @author <Authors name>
 * @version 1.0
 * @since <pre>Aug 11, 2015</pre>
 */
public class CodeTest {

    @Before
    public void before() throws Exception {

    }

    @After
    public void after() throws Exception {

    }

//    /**
//     * Method: guess(URL url)
//     */
//    @Test
//    public void testGuess1() throws Exception {
//        Code code = new Code();
//        String guessCode = code.guess(new File("/home/shaoaq/Downloads/codetest_dycn.jpg").toURI().toURL());
//        System.out.println(guessCode);
//    }

    /**
     * Method: guess(URL url)
     */
    @Test
    public void testGuess() throws Exception {
        Code code = new Code();
        int testCount = 100;
        int correctCount = 0;
        for (int i = 0; i < 100; i++) {
            if (check(code)) {
                correctCount++;
            }
        }
        System.out.println("checked :" + testCount + ",correct:" + correctCount);
    }

    private boolean check(Code code) throws IOException, InterruptedException {
        File file = File.createTempFile("guahao", "code");

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
        FileUtils.writeByteArrayToFile(file, bytes);

        String guessCode = code.guess(file.toURI().toURL());


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

        if (correct) {
            file.delete();
        } else {
            file.renameTo(new File("/tmp/codetest_" + guessCode + ".jpg"));
        }
        return correct;
    }
}
