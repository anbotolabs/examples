package xyz.anboto.example;

import com.alibaba.fastjson.JSON;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Client {
    final static String API_KEY = "xxx";
    final static byte[] API_SECRET = Base64.getDecoder().decode("xxx");
    final static String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    final static String RECV_WINDOW = "5000";
    final static String X_API_KEY = "X-API-KEY";
    final static String X_TIMESTAMP = "X-TIMESTAMP";
    final static String X_RECV_WINDOW = "X-RECV-WINDOW";
    final static String X_SIGN = "X-SIGN";

    final static AtomicInteger IDS = new AtomicInteger();
    protected static final String TEST_NET = "https://api.testnet.anboto.xyz";
    final static  String domain = TEST_NET + "/api/v2/trading";


    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
        Client test = new Client();

        long orderId = test.placeOrder();
        test.cancelOrder(orderId);
        test.placeManyOrders();

        List<Long> openOrders = test.getActiveOrders();
        System.out.println(openOrders.size() +" open orders : "+openOrders);

        test.cancelMany(openOrders);

    }

    public long placeOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = orderMap("BTCUSDT","BUY",0.001);
        Map<String, Object> response = JSON.parseObject(post("/order/create", map), Map.class); ;

        return Long.parseLong(response.get("order_id").toString());
    }

    public List<Long> getActiveOrders() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> response = get("/order/open", Collections.emptyMap());

        System.out.println("ACTIVE: " + response);

        List<Map<String,Object>> orders = (List<Map<String,Object>>)response.get("orders");
        return orders.stream().map(o -> Long.parseLong(o.get("order_id").toString())).collect(Collectors.toList());
    }

    public void cancelOrder(long orderId) throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new HashMap<>();
        map.put("order_id",orderId);

        post("/order/cancel",map);
    }

    public void cancelMany(List<Long> orderIds) throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new HashMap<>();
        List<Map<String, Object>> cancelRequests = new ArrayList<>();
        for(long id : orderIds){
            Map<String, Object> req = new HashMap<>();
            req.put("order_id",id);
            cancelRequests.add(req);
        }
        map.put("orders", cancelRequests);

        List<Map<String, Object>> response = JSON.parseObject(post("/order/cancelMany",map), List.class);
        System.out.println(response.size() +" cancel requests : "+ response);
    }

    public void placeManyOrders() throws NoSuchAlgorithmException, InvalidKeyException {
        List<Map<String, Object>> ordersList = new ArrayList<>();

        // Loop 200 times
        for (int i = 0; i < 200; i++) {
            // Call the orderMap function and add the result to the list
            Map<String, Object> o1 = orderMap("BTCUSDT", "BUY", 0.001);
            ordersList.add(o1);
        }

        Map<String, Object> many = new HashMap<>();
        many.put("orders", ordersList);

        Map<String, Object> response = JSON.parseObject(post("/order/createMany", many), Map.class);
        List<Map<String, Object>> statuses = (List<Map<String, Object>>)response.get("orders");
        System.out.println(statuses.size() +" order requests : "+ response);
    }

    @NotNull
    private static Map<String, Object> orderMap(String symbol, String side, double qty) {
        Map<String, Object> map = new HashMap<>();
        map.put("client_order_id",IDS.incrementAndGet()+"_"+Long.toString(System.currentTimeMillis()));
        map.put("symbol", symbol);
        map.put("side", side);
        map.put("asset_category", "SPOT");
        map.put("exchange", "BINANCE");
        map.put("strategy", "TWAP");
        map.put("quantity", qty);
        map.put("params", Map.of("duration_seconds", 120));

        return map;
    }
    private static String post(String path, Map<String, Object> map) throws NoSuchAlgorithmException, InvalidKeyException {
        String signature = genPostSign(map);
        String jsonMap = JSON.toJSONString(map);

        OkHttpClient client = new OkHttpClient().newBuilder().build();
        MediaType mediaType = MediaType.parse("application/json");

        Request request = new Request.Builder()
                .url(domain+path)
                .post(RequestBody.create(mediaType, jsonMap))
                .addHeader(X_API_KEY, API_KEY)
                .addHeader(X_SIGN, signature)
                .addHeader(X_TIMESTAMP, TIMESTAMP)
                .addHeader(X_RECV_WINDOW, RECV_WINDOW)
                .addHeader("Content-Type", "application/json")
                .build();
        Call call = client.newCall(request);
        try(Response response = call.execute()) {
            String responseJson = response.body().string();
            System.out.println("API RESPONSE: ["+response.message() +"] :" + responseJson);
            return responseJson;
        }catch (IOException e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static Map<String,Object> get(String path, Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
        String queryString = genQueryStr(params);
        String signature = genGetSign(queryString);

        OkHttpClient client = new OkHttpClient().newBuilder().build();

        String queryParams = queryString==null?"":("?"+queryString);
        Request request = new Request.Builder()
                .url(domain+path+queryParams)
                .get()
                .addHeader(X_API_KEY, API_KEY)
                .addHeader(X_SIGN, signature)
                .addHeader(X_TIMESTAMP, TIMESTAMP)
                .addHeader(X_RECV_WINDOW, RECV_WINDOW)
                .addHeader("Content-Type", "application/json")
                .build();
        Call call = client.newCall(request);
        try(Response response = call.execute()) {
            String responseJson = response.body().string();
            System.out.println("API RESPONSE: ["+response.message() +"] :" + responseJson);
            return JSON.parseObject(responseJson, Map.class);
        }catch (IOException e){
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * The way to generate the sign for POST requests
     * @param params: Map input parameters
     * @return signature used to be a parameter in the header
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genPostSign(Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET, "HmacSHA256");
        sha256_HMAC.init(secret_key);

        String paramJson = JSON.toJSONString(params);
        String sb = TIMESTAMP + API_KEY + RECV_WINDOW + paramJson;
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(sb.getBytes()));
    }

    /**
     * The way to generate the sign for GET requests
     * @param queryString: The queryString
     * @return signature used to be a parameter in the header
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genGetSign(String queryString) throws NoSuchAlgorithmException, InvalidKeyException {

        String sign = TIMESTAMP + API_KEY + RECV_WINDOW + (queryString==null?"":queryString);

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET, "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(sign.getBytes()));
    }

    /**
     * To convert bytes to hex
     * @param hash
     * @return hex string
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    /**
     * To generate query string for GET requests
     * @param map
     * @return
     */
    private static String genQueryStr(Map<String, Object> map) {
        if(map == null  || map.isEmpty()) return null;

        Set<String> keySet = map.keySet();
        Iterator<String> iter = keySet.iterator();
        StringBuilder sb = new StringBuilder();
        while (iter.hasNext()) {
            String key = iter.next();
            sb.append(key)
                    .append("=")
                    .append(map.get(key))
                    .append("&");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }
}
