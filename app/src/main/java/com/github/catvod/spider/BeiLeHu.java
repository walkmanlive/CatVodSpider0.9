package com.github.catvod.spider;

import android.text.TextUtils;

import com.github.catvod.bean.Class;
import com.github.catvod.bean.Result;
import com.github.catvod.bean.Vod;
import com.github.catvod.crawler.Spider;
import com.github.catvod.net.OkHttp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 贝乐虎（儿童视频）
 * <p>
 * 由 BeiLeHu.smali 反编译移植而来，逻辑与原 smali 一一对应。
 * 视频 ID 的格式为：播放地址@@标题@@封面
 */
public class BeiLeHu extends Spider {

    private static final String API = "https://vd.ubestkid.com/api/v1/bv/video";
    private static final String APP_VER = "6.1.9";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36";
    private static final String NAME = "贝乐虎";
    private static final String SEP = "@@";

    /**
     * 分类：subcateId -> 名称
     */
    private static final LinkedHashMap<String, String> CLASSES = new LinkedHashMap<>();

    static {
        CLASSES.put("56", "儿歌");
        CLASSES.put("63", "故事");
        CLASSES.put("62", "少儿");
        CLASSES.put("61", "古诗");
        CLASSES.put("64", "认知");
        CLASSES.put("65", "习惯");
    }

    /**
     * 请求视频列表，返回 result.items（请求结果没有 result 时返回 null）
     */
    private static JSONArray fetch(int page, int subcateId, Map<String, String> header) throws Exception {
        JSONObject body = new JSONObject();
        body.put("age", 1);
        body.put("appver", APP_VER);
        body.put("egvip_status", 0);
        body.put("svip_status", 0);
        body.put("vps", 60);
        body.put("subcateId", subcateId);
        body.put("p", page);
        String resp = OkHttp.post(API, body.toString(), header).getBody();
        JSONObject result = new JSONObject(resp).optJSONObject("result");
        return result == null ? null : result.optJSONArray("items");
    }

    /**
     * 解析视频列表，limit <= 0 表示不限制数量
     */
    private static List<Vod> parseVods(JSONArray items, int limit) throws Exception {
        List<Vod> list = new ArrayList<>();
        if (items == null) return list;
        int size = Math.min(items.length(), limit > 0 ? limit : items.length());
        for (int i = 0; i < size; i++) {
            JSONObject item = items.getJSONObject(i);
            String url = item.optString("url");
            if (TextUtils.isEmpty(url)) continue;
            String title = item.optString("title", NAME);
            String image = item.optString("image");
            String id = url + SEP + title + SEP + image;
            // 备注优先显示时长，没有时长则显示 brand
            String remarks = formatDuration(item.optInt("endInterval", 0));
            if (TextUtils.isEmpty(remarks)) remarks = item.optString("brand", "");
            list.add(new Vod(id, title, image, remarks));
        }
        return list;
    }

    /**
     * endInterval 的单位为 0.1 秒，转换为 m:ss
     */
    private static String formatDuration(int endInterval) {
        if (endInterval <= 0) return "";
        int seconds = endInterval / 10;
        int minute = seconds / 60;
        int second = seconds % 60;
        return minute + (second < 10 ? ":0" : ":") + second;
    }

    @Override
    public String homeContent(boolean filter) throws Exception {
        List<Class> classes = new ArrayList<>();
        for (Map.Entry<String, String> entry : CLASSES.entrySet()) {
            classes.add(new Class(entry.getKey(), entry.getValue()));
        }
        Map<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/json");
        header.put("appver", APP_VER);
        JSONArray items = fetch(1, 1, header);
        return Result.string(classes, parseVods(items, 20));
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) throws Exception {
        int page;
        try {
            page = Integer.parseInt(pg);
        } catch (Exception e) {
            page = 1;
        }
        Map<String, String> header = new HashMap<>();
        header.put("Content-Type", "application/json");
        header.put("User-Agent", UA);
        JSONArray items = fetch(page, Integer.parseInt(tid), header);
        return Result.string(parseVods(items, 0));
    }

    @Override
    public String detailContent(List<String> ids) throws Exception {
        String id = ids.get(0);
        String[] parts = id.split(SEP);
        Vod vod = new Vod();
        vod.setVodId(id);
        vod.setVodName(parts.length > 1 ? parts[1] : NAME);
        if (parts.length > 2) vod.setVodPic(parts[2]);
        vod.setTypeName("少儿");
        vod.setVodPlayFrom(NAME);
        vod.setVodPlayUrl("播放$" + parts[0]);
        return Result.string(vod);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) throws Exception {
        // 与原 smali 保持一致：id 中含“播放”时取其前面的部分；含“$”时取“$”之后的部分
        if (id.contains("播放")) id = id.split("播放")[0];
        if (id.contains("$")) id = id.substring(id.indexOf('$') + 1);
        Map<String, String> header = new HashMap<>();
        header.put("User-Agent", UA);
        return Result.get().url(id).header(header).string();
    }

    @Override
    public String searchContent(String key, boolean quick) throws Exception {
        // 原版不支持搜索，直接返回空列表
        return Result.string(new ArrayList<Vod>());
    }
}
