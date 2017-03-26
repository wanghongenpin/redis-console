package com.whe.redis.web;

import com.alibaba.fastjson.JSON;
import com.whe.redis.service.StandAloneService;
import com.whe.redis.util.JedisFactory;
import com.whe.redis.util.Page;
import com.whe.redis.util.ServerConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.Tuple;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Created by wang hongen on 2017/1/12.
 * Redis控制台
 *
 * @author wanghongen
 */
@Controller
@RequestMapping("/standalone")
public class StandaloneController {
    private static final Logger log = LoggerFactory.getLogger(StandaloneController.class);

    @Resource
    private StandAloneService standAloneService;

    private String contextPath = null;

    /**
     * 入口 首页
     *
     * @param model model
     * @return index
     */
    @RequestMapping(value = {"/index"})
    public String index(@RequestParam(defaultValue = "0") String cursor, String match,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        if (contextPath == null) {
            contextPath = request.getContextPath();
        }
        try {
            //分页map  name页数对应分页
            Map<String, Map<Integer, List<String>>> nameAndPageMap = new HashMap<>();

            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean expandedServer = true;
            for (String name : JedisFactory.getStandaloneMap().keySet()) {

                Integer size = standAloneService.getDataBasesSize(name);
                model.addAttribute("dataSize", size);

                String serverTree = serverTree(name, expandedServer);
                sb.append(serverTree);
                expandedServer = false;

                sb.append("[");

                //对应节点db和游标
                Map<Integer, List<String>> cursorMap = new HashMap<>();

                //获得数据库角标和对应数据库里元素数量
                Map<Integer, Long> dataBases = standAloneService.getDataBases(name);
                boolean expanded = true;
                for (Map.Entry<Integer, Long> entry : dataBases.entrySet()) {
                    Integer db = entry.getKey();
                    Long dbSize = entry.getValue();

                    String dbTree = dbTree(db, dbSize, expanded);
                    sb.append(dbTree);
                    expanded = false;

                    cursorMap.clear();
                    //当前页游标 保存到cookie
                    List<String> cursorList = new ArrayList<>();
                    cursorList.add(cursor);
                    cursorMap.put(db, cursorList);

                    String keyTree = keyTree(name, db, cursor, cursor, match);
                    sb.append(keyTree);

                    sb.append("},");
                }
                sb.deleteCharAt(sb.length() - 1).append("]},");

                nameAndPageMap.put(name, cursorMap);
            }
            sb.deleteCharAt(sb.length() - 1);
            if (sb.toString().equals("")) {
                sb.append("[]");
            } else {
                sb.append("]");
            }
            //分页保存到cookie
            String jsonString = JSON.toJSONString(nameAndPageMap);
            String encode = URLEncoder.encode(jsonString, ServerConstant.CHARSET);
            Cookie cookie = new Cookie(ServerConstant.REDIS_CURSOR, encode);
            cookie.setPath("/");
            cookie.setMaxAge(-1);
            response.addCookie(cookie);

            model.addAttribute("tree", sb.toString());
            model.addAttribute("match", match);
            model.addAttribute("server", "/standalone");
        } catch (Exception e) {
            log.error("StandaloneController index error:" + e.getMessage(), e);
        }
        return "index";
    }

    @RequestMapping("/addServer")
    @ResponseBody
    public String addServer(String name) {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("[");
        String serverTree = serverTree(name, false);
        stringBuilder.append(serverTree);

        stringBuilder.append("[");
        //获得数据库角标和对应数据库里元素数量
        Map<Integer, Long> dataBases = standAloneService.getDataBases(name);
        for (Map.Entry<Integer, Long> entry : dataBases.entrySet()) {
            Integer db = entry.getKey();
            Long dbSize = entry.getValue();

            String dbTree = dbTree(db, dbSize, false);
            stringBuilder.append(dbTree);
            stringBuilder.append("[]},");
        }
        stringBuilder.deleteCharAt(stringBuilder.length()-1).append("]}]");
        return stringBuilder.toString();
    }

    @RequestMapping("/save")
    @ResponseBody
    public String save(String name, String redis_key, Integer redis_data_size, String redis_type, Double redis_score, String redis_field, String redis_value, String redis_serializable) {
        try {
            if ("1".equals(redis_serializable)) {
                switch (redis_type) {
                    case ServerConstant.REDIS_STRING:
                        return standAloneService.setNxSerialize(name, redis_data_size, redis_key, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_HASH:
                        return standAloneService.hSetNxSerialize(name, redis_data_size, redis_key, redis_field, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_LIST:
                        return standAloneService.lPushSerialize(name, redis_data_size, redis_key, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_SET:
                        return standAloneService.sAddSerialize(name, redis_data_size, redis_key, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_ZSET:
                        return standAloneService.zAddSerialize(name, redis_data_size, redis_key, redis_score, redis_value) == 1 ? "1" : "2";
                }
            } else {
                switch (redis_type) {
                    case ServerConstant.REDIS_STRING:
                        return standAloneService.setNx(name, redis_data_size, redis_key, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_HASH:
                        return standAloneService.hSetNx(name, redis_data_size, redis_key, redis_field, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_LIST:
                        return standAloneService.lPush(name, redis_data_size, redis_key, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_SET:
                        return standAloneService.sAdd(name, redis_data_size, redis_key, redis_value) == 1 ? "1" : "2";
                    case ServerConstant.REDIS_ZSET:
                        return standAloneService.zAdd(name, redis_data_size, redis_key, redis_score, redis_value) == 1 ? "1" : "2";
                }
            }
        } catch (Exception e) {
            log.error("StandaloneController save error:" + e.getMessage(), e);
            return e.getMessage();
        }
        return "0";
    }

    /**
     * ajax加载string类型数据
     *
     * @return String
     */
    @RequestMapping(value = {"/getString"})
    @ResponseBody
    public String getString(String name, Integer db, String key) {
        try {
            return standAloneService.getString(name, db, key);
        } catch (Exception e) {
            log.error("StandaloneController getString error[db=" + db + ",key=" + key + "]" + e.getMessage(), e);
        }
        return null;
    }

    @RequestMapping(value = {"/serialize/getString"})
    @ResponseBody
    public String getSerializeString(String name, Integer db, String key) {
        try {
            return standAloneService.getStringSerialize(name, db, key);
        } catch (UnsupportedEncodingException e) {
            log.error("StandaloneController getSerializeString error[db=" + db + ",key=" + key + "]" + e.getMessage(), e);
        }
        return null;
    }

    /**
     * ajax分页加载list类型数据
     *
     * @return map
     */
    @RequestMapping(value = {"/getList"})
    @ResponseBody
    public Page<List<String>> getList(String name, int db, String key, int pageNo, HttpServletRequest request) {
        try {
            Page<List<String>> page = standAloneService.findListPageByKey(name, db, key, pageNo);
            page.pageViewAjax(request.getContextPath() + "/getList", "");
            return page;
        } catch (Exception e) {
            log.error("StandaloneController getList error:" + e.getMessage(), e);
        }
        return null;
    }

    @RequestMapping(value = {"/serialize/getList"})
    @ResponseBody
    public Page<List<String>> getSerializeList(String name, int db, String key, int pageNo, HttpServletRequest request) {
        Page<List<String>> page = null;
        try {
            page = standAloneService.findListPageByKeySerialize(name, db, key, pageNo);
            page.pageViewAjax(request.getContextPath() + "/serialize/getList", "");
        } catch (UnsupportedEncodingException e) {
            log.error("StandaloneController getSerializeList error:" + e.getMessage(), e);
        }
        return page;
    }

    /**
     * ajax加载set类型数据
     *
     * @return map
     */
    @RequestMapping(value = {"/getSet"})
    @ResponseBody
    public Set<String> getSet(String name, int db, String key) {
        return standAloneService.getSet(name, db, key);
    }

    @RequestMapping(value = {"/serialize/getSet"})
    @ResponseBody
    public Set<String> getSerializeSet(String name, int db, String key) {
        try {
            return standAloneService.getSetSerialize(name, db, key);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ajax加载zSet类型数据
     *
     * @return map
     */
    @RequestMapping(value = {"/getZSet"})
    @ResponseBody
    public Page<Set<Tuple>> getZSet(String name, int db, int pageNo, String key) {
        Page<Set<Tuple>> page = standAloneService.findZSetPageByKey(name, db, pageNo, key);
        page.pageViewAjax("/getZSet", "");
        return page;
    }

    @RequestMapping(value = {"/serialize/getZSet"})
    @ResponseBody
    public Page<Set<Tuple>> getSerializeZSet(String name, int db, String key, int pageNo, HttpServletRequest request) {
        Page<Set<Tuple>> page = null;
        try {
            page = standAloneService.findZSetPageByKeySerialize(name, db, key, pageNo);
            page.pageViewAjax(request.getContextPath() + "/serialize/getList", "");
        } catch (UnsupportedEncodingException e) {
            log.error("StandaloneController getSerializeZSet error:" + e.getMessage(), e);
        }
        return page;
    }

    @RequestMapping(value = {"/hGetAll"})
    @ResponseBody
    public Map<String, String> hGetAll(String name, int db, String key) {
        return standAloneService.hGetAll(name, db, key);
    }

    @RequestMapping(value = {"/serialize/hGetAll"})
    @ResponseBody
    public Map<String, String> hGetAllSerialize(String name, int db, String key) {
        try {
            return standAloneService.hGetAllSerialize(name, db, key);
        } catch (UnsupportedEncodingException e) {
            log.error("StandaloneController hGetAllSerialize error:" + e.getMessage(), e);
        }
        return null;
    }

    /**
     * key重命名
     *
     * @param db     db
     * @param oldKey 旧key
     * @param newKey 新key
     * @return 2:key已存在;1:成功;0:失败
     */
    @RequestMapping(value = {"/renameNx"})
    @ResponseBody
    public String renameNx(String name, int db, String oldKey, String newKey) {
        try {
            return standAloneService.renameNx(name, db, oldKey, newKey) == 0 ? "2" : "1";
        } catch (Exception e) {
            log.error("StandaloneController renameNx error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * 获得生存时间
     *
     * @param db  db
     * @param key key
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/ttl"})
    @ResponseBody
    public long ttl(String name, int db, String key) {
        try {
            return standAloneService.ttl(name, db, key);
        } catch (Exception e) {
            log.error("StandaloneController ttl error:" + e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 更新生存时间
     *
     * @param db      db
     * @param key     key
     * @param seconds 秒
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/setExpire"})
    @ResponseBody
    public String setExpire(String name, int db, String key, int seconds) {
        try {

            standAloneService.setExpire(name, db, key, seconds);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController setExpire error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * 移除 key 的生存时间
     *
     * @param db  db
     * @param key key
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/persist"})
    @ResponseBody
    public String persist(String name, int db, String key) {
        try {
            standAloneService.persist(name, db, key);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController persist error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * 删除key
     *
     * @param db  db
     * @param key key
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/delKey"})
    @ResponseBody
    public String delKey(String name, int db, String key) {
        try {
            standAloneService.delKey(name, db, key);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController delKey error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * string更新值
     *
     * @param db  db
     * @param key key
     * @param val newValue
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/updateString"})
    @ResponseBody
    public String updateString(String name, int db, String key, String val) {
        try {
            standAloneService.set(name, db, key, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateString error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping("/serialize/updateString")
    @ResponseBody
    public String updateStringSerialize(String name, int db, String key, String val) {
        try {
            standAloneService.setSerialize(name, db, key, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateStringSerialize error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * list根据索引更新value
     *
     * @param db    db
     * @param index index
     * @param key   key
     * @param val   val
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/updateList"})
    @ResponseBody
    public String updateList(String name, int db, int index, String key, String val) {
        try {
            standAloneService.lSet(name, db, index, key, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateList error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/serialize/updateList"})
    @ResponseBody
    public String updateListSerialize(String name, int db, int index, String key, String val) {
        try {
            standAloneService.lSetSerialize(name, db, index, key, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController ResponseBody error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * list根据索引删除
     *
     * @param db       db
     * @param listSize listSize
     * @param index    index
     * @param key      key
     * @return 1:成功;0:失败
     */
    @RequestMapping(value = {"/delList"})
    @ResponseBody
    public String delList(String name, int db, int listSize, int index, String key) {
        try {
            long lLen = standAloneService.lLen(name, db, key);
            if (listSize != lLen) {
                return "2";
            }
            standAloneService.lRem(name, db, index, key);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController delList error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }


    @RequestMapping(value = {"/updateSet"})
    @ResponseBody
    public String updateSet(String name, int db, String key, String oldVal, String newVal) {
        try {
            standAloneService.updateSet(name, db, key, oldVal, newVal);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateSet error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/serialize/updateSet"})
    @ResponseBody
    public String updateSetSerialize(String name, int db, String key, String oldVal, String newVal) {
        try {
            standAloneService.updateSetSerialize(name, db, key, oldVal, newVal);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateSetSerialize error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/delSet"})
    @ResponseBody
    public String delSet(String name, int db, String key, String val) {
        try {
            standAloneService.delSet(name, db, key, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController delSet error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/updateZSet"})
    @ResponseBody
    public String updateZSet(String name, int db, String key, String oldVal, String newVal, double score) {
        try {
            standAloneService.updateZSet(name, db, key, oldVal, newVal, score);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateZSet error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/serialize/updateZSet"})
    @ResponseBody
    public String updateZSetSerialize(String name, int db, String key, String oldVal, String newVal, double score) {
        try {
            standAloneService.updateZSetSerialize(name, db, key, oldVal, newVal, score);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController updateZSetSerialize error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/delZSet"})
    @ResponseBody
    public String delZSet(String name, int db, String key, String val) {
        try {
            standAloneService.delZSet(name, db, key, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController delZSet error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/hSet"})
    @ResponseBody
    public String hSet(String name, int db, String key, String field, String val) {
        try {
            standAloneService.hSet(name, db, key, field, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController hSet error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/serialize/hSet"})
    @ResponseBody
    public String hSetSerialize(String name, int db, String key, String field, String val) {
        try {
            standAloneService.hSetSerialize(name, db, key, field, val);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController hSetSerialize error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/updateHash"})
    @ResponseBody
    public String updateHash(String name, int db, String key, String oldField, String newField, String val) {
        try {
            return standAloneService.updateHash(name, db, key, oldField, newField, val) ? "1" : "2";
        } catch (Exception e) {
            log.error("StandaloneController updateHash error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/serialize/updateHash"})
    @ResponseBody
    public String updateHashSerialize(String name, int db, String key, String oldField, String newField, String val) {
        try {
            return standAloneService.updateHashSerialize(name, db, key, oldField, newField, val) ? "1" : "2";
        } catch (Exception e) {
            log.error("StandaloneController updateHashSerialize error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping(value = {"/delHash"})
    @ResponseBody
    public String delHash(String name, int db, String key, String field) {
        try {
            standAloneService.delHash(name, db, key, field);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController delHash error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    /**
     * 备份数据
     *
     * @param response response
     * @throws IOException IOException
     */
    @RequestMapping("/backup")
    public void backup(String name, HttpServletResponse response) {
        try {
            String str = standAloneService.backup(name);
            LocalDate data = LocalDate.now();
            log.info("StandaloneController backup info:" + data);
            response.setContentType("text/plain; charset=utf-8");//设置MIME类型
            response.setHeader("Content-Disposition", "attachment; filename=" + data + "standalone.redis");
            response.getWriter().write(str);
        } catch (Exception e) {
            log.error("StandaloneController backup error:" + e.getMessage(), e);
        }
    }

    /**
     * 恢复数据
     *
     * @param file file
     * @return string
     */
    @RequestMapping("/recover")
    @ResponseBody
    public String recover(String name, MultipartFile file) {
        try {
            String data = new String(file.getBytes(), ServerConstant.CHARSET);
            Object obj = JSON.parse(data);
            if (obj instanceof Map) {
                Map<String, Map> map = (Map) obj;
                boolean isCluster = false;
                for (Map.Entry<String, Map> entry : map.entrySet()) {
                    Map nowMap;
                    int db;
                    try {
                        db = Integer.parseInt(entry.getKey());
                        nowMap = entry.getValue();
                    } catch (Exception e) {
                        isCluster = true;
                        nowMap = map;
                        db = 0;
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_STRING)) {
                        standAloneService.saveAllString(name, db, (Map<String, String>) nowMap.get(ServerConstant.REDIS_STRING));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_HASH)) {
                        standAloneService.saveAllHash(name, db, (Map) nowMap.get(ServerConstant.REDIS_HASH));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_LIST)) {
                        standAloneService.saveAllList(name, db, (Map) nowMap.get(ServerConstant.REDIS_LIST));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_SET)) {
                        standAloneService.saveAllSet(name, db, (Map) nowMap.get(ServerConstant.REDIS_SET));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_ZSET)) {
                        standAloneService.saveAllZSet(name, db, (Map) nowMap.get(ServerConstant.REDIS_ZSET));
                    }
                    if (isCluster) {
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("StandaloneController recover error:" + e.getMessage(), e);
            return e.getMessage();
        }
        return "1";
    }

    /**
     * 序列化恢复数据
     *
     * @return string
     */
    @RequestMapping("/serializeRecover")
    @ResponseBody
    public String serializeRecover(String name, MultipartFile file) {
        try {
            String data = new String(file.getBytes(), ServerConstant.CHARSET);
            Object obj = JSON.parse(data);
            if (obj instanceof Map) {
                Map<String, Map> map = (Map) obj;
                boolean isCluster = false;
                for (Map.Entry<String, Map> entry : map.entrySet()) {
                    Map nowMap;
                    int db;
                    try {
                        db = Integer.parseInt(entry.getKey());
                        nowMap = entry.getValue();
                    } catch (Exception e) {
                        db = 0;
                        isCluster = true;
                        nowMap = map;
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_STRING)) {
                        standAloneService.saveAllStringSerialize(name, db, (Map<String, String>) nowMap.get(ServerConstant.REDIS_STRING));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_LIST)) {
                        standAloneService.saveAllListSerialize(name, db, (Map) nowMap.get(ServerConstant.REDIS_LIST));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_SET)) {
                        standAloneService.saveAllSetSerialize(name, db, (Map) nowMap.get(ServerConstant.REDIS_SET));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_ZSET)) {
                        standAloneService.saveAllZSetSerialize(name, db, (Map) nowMap.get(ServerConstant.REDIS_ZSET));
                    }
                    if (nowMap.containsKey(ServerConstant.REDIS_HASH)) {
                        standAloneService.saveAllHashSerialize(name, db, (Map) nowMap.get(ServerConstant.REDIS_HASH));
                    }
                    if (isCluster) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("StandaloneController serializeRecover error:" + e.getMessage(), e);
            return e.getMessage();
        }
        return "1";
    }

    /**
     * 删除所有
     *
     * @return string
     */
    @RequestMapping("/flushAll")
    @ResponseBody
    public String flushAll(String name) {
        try {
            standAloneService.flushAll(name);
            return "1";
        } catch (Exception e) {
            log.error("StandaloneController flushAll error:" + e.getMessage(), e);
            return e.getMessage();
        }
    }

    @RequestMapping("/upPage")
    @ResponseBody
    public String upPage(String name, String db, String cursor, String match, HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String upCursor = getUpCursorByCookie(cookies, name, db, cursor);
        return keyTree(name, Integer.parseInt(db), cursor, upCursor, match);
    }

    /**
     * 下一页
     */
    @RequestMapping("/nextPage")
    @ResponseBody
    public String nextPage(String name, String db, String cursor, String match, HttpServletRequest request, HttpServletResponse
            response) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null && cookies.length > 0) {
            Optional<Cookie> cookie = Stream.of(cookies).filter(c -> c.getName().equals(ServerConstant.REDIS_CURSOR)).findAny();
            cookie.ifPresent(c -> {
                try {
                    String value = URLDecoder.decode(c.getValue(), ServerConstant.CHARSET);
                    Map<String, Map<String, List<String>>> nameAndPageMap = (Map<String, Map<String, List<String>>>) JSON.parseObject(value, Map.class);
                    List<String> list = nameAndPageMap.get(name).get(db);
                    list.add(cursor);
                    c.setValue(URLEncoder.encode(JSON.toJSONString(nameAndPageMap), ServerConstant.CHARSET));
                    response.addCookie(c);
                } catch (UnsupportedEncodingException e) {
                    log.error("StandaloneController nextPage error:" + e.getMessage(), e);
                }
            });
        }
        String upCursor = getUpCursorByCookie(cookies, name, db, cursor);
        return keyTree(name, Integer.parseInt(db), cursor, upCursor, match);
    }

    /**
     * 从cookie获得上一页游标
     */
    private String getUpCursorByCookie(Cookie[] cookies, String name, String db, String cursor) {
        if (ServerConstant.DEFAULT_CURSOR.equals(cursor)) {
            return ServerConstant.DEFAULT_CURSOR;
        }
        return Stream
                .of(cookies)
                .filter(c -> c.getName().equals(ServerConstant.REDIS_CURSOR))
                .findAny()
                .map(c -> {
                    String value = null;
                    try {
                        value = URLDecoder.decode(c.getValue(), ServerConstant.CHARSET);
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                    Map<String, Map> nameAndPageMap = JSON.parseObject(value, Map.class);
                    List<String> list = (List<String>) nameAndPageMap.get(name).get(db);
                    int size = list.size();
                    return IntStream
                            .range(0, size)
                            .filter(i -> list.get(i).equals(cursor))
                            .boxed()
                            .map(i -> list.get(i - 1))
                            .findAny().orElse(ServerConstant.DEFAULT_CURSOR);
                }).orElse(ServerConstant.DEFAULT_CURSOR);
    }

    private String serverTree(String name, boolean expanded) {
        StringBuilder sb = new StringBuilder();
        sb.append("{text:").append("'").append(name).append("',");
        sb.append("icon:'").append(contextPath).append("/static/img/redis.png',");
        if (expanded) {
            sb.append("expanded:").append(true).append(",");
        }
        sb.append("class:").append("'parent_li',");
        sb.append("nodes:");
        return sb.toString();
    }

    private String dbTree(Integer db, Long dbSize, boolean expanded) {
        StringBuilder sb = new StringBuilder();
        sb.append("{text:").append("'").append(ServerConstant.DB).append(db).append("',")
                .append("icon:'").append(contextPath).append("/static/img/db.png',")
                .append("tags:").append("['").append(dbSize).append("']");
        if (dbSize > 0 && expanded) {
            sb.append(",expanded:").append(true);
        }
        sb.append(",").append("nodes:");
        return sb.toString();
    }

    private String keyTree(String name, Integer db, String cursor, String upCursor, String match) {
        StringBuilder sb = new StringBuilder();
        ScanResult<String> scanResult = standAloneService.getKeysByDb(name, db, cursor, match);
        sb.append("[");
        //获得key对应的数据类型
        Map<String, String> typeMap = standAloneService.getType(name, db, scanResult.getResult());
        typeMap.forEach((key, type) -> sb.append("{text:").append("'").append(key).append("',icon:'").append(contextPath).append("/static/img/").append(type).append(".png").append("',type:'").append(type).append("'},"));
        String stringCursor = scanResult.getStringCursor();
        //分页样式
        sb.append("{page:").append("'<ul class=\"pagination\" style=\"margin:0px\"> <li ");
        if (cursor == null || "0".equals(cursor)) {
            sb.append(" class=\"disabled\" ");
        }
        sb.append("><a  href=\"javascript:void(0);\" onclick=\"upPage(").append(db).append(",").append(upCursor).append(",event)").append(" \">上一页</a></li><li ");
        if (ServerConstant.DEFAULT_CURSOR.equals(stringCursor)) {
            sb.append(" class=\"disabled\"");
        }
        sb.append("> <a  href=\"javascript:void(0);\" onclick=\"nextPage(").append(db).append(",").append(stringCursor).append(",event)").append(" \">下一页</a></li></ul>'}");
        sb.append("]");
        return sb.toString();
    }
}
