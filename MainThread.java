package com.cxsigin.laoxin;

import com.alibaba.fastjson.JSONObject;
import com.cxsigin.laoxin.utils.GetUtil;

import java.io.*;
import java.util.*;

import static java.lang.Thread.sleep;

public class MainThread {

    private static String username = "";   // 仅支持手机号(账号)
    private static String password = "";    // 密码

    // 可选项
    //cookie
    private static String cookie = "";
    //uid
    private static String uid = "";

    private static Scanner sc = new Scanner(System.in);
    private static Map header = new HashMap<>();
    private static List<String> notes = new ArrayList<>();

    static {
        Properties pro = new Properties();
        try {
            //加载配置文件
            String url = System.getProperty("user.dir");  // 获取软件运行的路径
            // System.out.println(url);
            FileInputStream is = new FileInputStream(url + "\\CXinfo.properties");
            pro.load(is);
            username = pro.getProperty("username");
            password = pro.getProperty("password");

        } catch (IOException e) {
            e.printStackTrace();
        }
        if (username == "" || username == null) {
            System.out.println("请输入用户名(只支持手机号)：");
            username = sc.nextLine();
        }
        if (password == "" || password == null) {
            System.out.print("请输入密码：");
            password = sc.next();
        }
        login();
        header.put("cookie", cookie);
    }


    public static void main(String[] args) {
        List<Map<String, String>> courseList = getCourseList();
        for (int i = 0; i < courseList.size(); i++) {
            System.out.println("索引: " + i + "--名称: " + courseList.get(i).get("name"));
        }

        System.out.println("----------------------请输入需要监控的课程索引(多个课程用-减号分割)------------------------------");
        String input = sc.next();
        String[] csIDs = input.split("-");
        System.out.println("监控速度(秒数):");
        int speed = Integer.parseInt(sc.next()) * 1000;
        System.out.println("输入1开始执行,输入其他字符结束!");
        String flag = sc.next();
        sc.close();
        if (flag.equalsIgnoreCase("1")) {
            System.out.println("--------------------开始监控----------------------------");
            for (int i = 0; i < csIDs.length; i++) {
                int index = Integer.parseInt(csIDs[i]);
                new Thread(() -> {
                    Thread.currentThread().setName("线程 : " + courseList.get(index).get("name"));
                    System.out.println("索引" + index + "监控的课程是: " + courseList.get(index).get("name") + "--课程ID: " + courseList.get(index).get("courseId") + "--班级ID: " + courseList.get(index).get("classId"));
                    StartTask(index, speed, courseList);
                }).start();
            }
        } else {
            System.out.println("已结束!");
        }
    }


    /**
     * 获取课程列表
     *
     * @return
     */
    private static List<Map<String, String>> getCourseList() {
        String url = "http://mooc1-api.chaoxing.com/mycourse";

        // 调用get请求
        JSONObject courseList = null;
        try {
            String get = GetUtil.sendGet(url, null, header);
            courseList = (JSONObject) JSONObject.parse(get);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (courseList != null) {  // 确认已正确获取数据
            List<Map<String, Object>> list = (List) courseList.get("channelList");
            //System.out.println(list.size());
            List<Map<String, String>> courseData = new ArrayList<>();
            if (list != null) {
                for (Map<String, Object> o : list) {
                    if (!o.toString().contains("course")) { // 判断是否为课程
                        continue;
                    }
                    Map<String, Object> content = (Map<String, Object>) o.get("content");
                    Map<String, Object> course = (Map<String, Object>) content.get("course");
                    List<Map<String, Object>> data = (List<Map<String, Object>>) course.get("data");
                    Map<String, String> temp = new HashMap<>();
                    //System.out.println(data.get(0).get("id").toString());
                    temp.put("courseId", data.get(0).get("id").toString());
                    temp.put("name", data.get(0).get("name").toString());
                    //courseData.put("imageurl",data.get(0).get("imageurl"));
                    temp.put("classId", content.get("id").toString());
                    courseData.add(temp);
                }
                System.out.println("获取课程列表成功！");
                //System.out.println(list.toString());
            } else {
                System.out.println("获取课程列表失败！");
            }
            return courseData;
        }
        return null;
    }

    /**
     * 签到任务检测
     *
     * @param index
     * @param speed
     * @param courseDate
     */
    private static void StartTask(int index, int speed, List<Map<String, String>> courseDate) {
        String url = "https://mobilelearn.chaoxing.com/ppt/activeAPI/taskactivelist?courseId=" + courseDate.get(index).get("courseId") + "&classId=" + courseDate.get(index).get("classId") + "&uid=" + uid;
        JSONObject result = null;
        while (true) {
            try {
                sleep(speed);
                String get = GetUtil.sendGet(url, null, header);
                result = (JSONObject) JSONObject.parse(get);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                // 发生错误cookie可能失效
                login();
                header.clear();
                header.put("cookie", cookie);
            }
            if (result != null) {
                List<Map<String, Object>> activeList = (List) result.get("activeList");

                String apId = "";
                for (Map<String, Object> map : activeList) {
                /*System.out.println(map);
                System.out.println(map.get("activeType") +"---"+ map.get("status"));*/

                    if (map.get("activeType").equals(2) && map.get("status").equals(1)) { // 判断是否为签到任务
                        apId = map.get("id").toString();
                        //System.out.println(!notes.contains(apId));
                        if (!notes.contains(apId)) {  // 判断课程是否已签到
                            // 开始签到
                            sign(apId);
                            break;  // 一般只有一次签到，循环结束语默认启用
                        }
                    }
                }
            }
        }
    }


    /**
     * 签到方法
     *
     * @param apID
     */
    private static void sign(String apID) {
        System.out.println("【签到】" + Thread.currentThread().getName() + " : 已检测到签到任务,开始签到!");
        String url = "https://mobilelearn.chaoxing.com/pptSign/stuSignajax?activeId=" + apID + "&uid" + uid + "=&clientip=&latitude=-1&longitude=-1&appType=15&fid=0";
        String flag = null;
        try {
            flag = GetUtil.sendGet(url, null, header);
        } catch (IOException e) {
            e.printStackTrace();
            // 发生错误cookie可能失效
            login();
            header.clear();
            header.put("cookie", cookie);
        }

        if (!flag.contains("已签到")) {
            notes.add(apID); // 添加已签到任务id
            System.out.println("【" + Thread.currentThread().getName() + "】" + apID + "已签到！");
        }
    }


    /**
     * 登陆并设置cookie和uid
     */
    private static void login() {
        String url = "https://passport2.chaoxing.com/fanyalogin";
        Base64.Encoder encoder = Base64.getEncoder();
        String pid = "";
        try {
            pid = encoder.encodeToString(password.getBytes("utf-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        Map header = new HashMap<>();
        header.put("Referer", "https://passport2.chaoxing.com/login?fid=&newversion=true&refer=http://fxlogin.chaoxing.com/findlogin.jsp?backurl=http://www.chaoxing.com/channelcookie");

        String param = "?ifd=-1&uname=" + username + "&refer=http://fxlogin.chaoxing.com/findlogin.jsp?backurl=http://www.chaoxing.com/channelcookie" +
                "&password=" + pid + "&t=true";
        List<String> cookieList = null;
        try {
            cookieList = GetUtil.getCookie(url, param, header);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (cookieList.toString().contains("uid")) {
            String temp = cookieList.toString();
            String cookies = temp.substring(1, temp.length() - 1);
            String[] split = cookies.split("=");
            List<String> asList = Arrays.asList(split);

            int index = asList.indexOf("/, _uid");
            uid = asList.get(index + 1).split(";")[0];
            cookie = cookies;
        } else {
            System.out.println("用户名或密码错误!");
            System.out.println("------------------------------请手动输入用户名和密码--------------------------------------");
            System.out.println("请输入用户名(只支持手机号)：");
            username = sc.next();
            System.out.println("请输入密码：");
            password = sc.next();
            System.out.println("------------------------------请手动输入用户名和密码--------------------------------------");
            login();
        }
    }
}
