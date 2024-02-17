package xyz.anboto.example;

import com.alibaba.fastjson.JSON;
import okhttp3.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;

public class Client {
    final static String API_KEY = "xxx";
    final static byte[] API_SECRET = Base64.getDecoder().decode("xxx");
    final static String TIMESTAMP = Long.toString(ZonedDateTime.now().toInstant().toEpochMilli());
    final static String RECV_WINDOW = "5000";
    final static String X_API_KEY = "X-API-KEY";
    final static String X_TIMESTAMP = "X-TIMESTAMP";
    final static String X_RECV_WINDOW = "X-RECV-WINDOW";
    final static String X_SIGN = "X-SIGN";
    final static  String domain = "http://api.testnet.anboto.xyz/api/v2/trading";


    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeyException {
        Client test = new Client();

        test.placeManyOrders();

//        encryptionTest.getOpenOrder();
    }

    public void placeOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new HashMap<>();
        map.put("client_order_id",Long.toString(System.currentTimeMillis()));
        map.put("symbol", "BTCUSDT");
        map.put("side", "BUY");
        map.put("asset_category", "SPOT");
        map.put("exchange", "BINANCE");
        map.put("strategy", "TWAP");
        map.put("quantity", 0.001);


        send("/order/create",map);
    }

    public void cancelOrder() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> map = new HashMap<>();
        map.put("client_order_id","1708169120400");

        send("/order/cancel",map);
    }

    public void placeManyOrders() throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, Object> o1 = new HashMap<>();
        o1.put("client_order_id",Long.toString(System.currentTimeMillis()));
        o1.put("symbol", "BTCUSDT");
        o1.put("side", "BUY");
        o1.put("asset_category", "SPOT");
        o1.put("exchange", "BINANCE");
        o1.put("strategy", "TWAP");
        o1.put("quantity", 0.001);

        Map<String, Object> o2 = new HashMap<>();
        o2.put("client_order_id",Long.toString(System.currentTimeMillis()));
        o2.put("symbol", "ETHUSDT");
        o2.put("side", "SELL");
        o2.put("asset_category", "SPOT");
        o2.put("exchange", "BINANCE");
        o2.put("strategy", "TWAP");
        o2.put("quantity", 0.01);

        Map<String, Object> many = new HashMap<>();
        many.put("orders", List.of(o1,o2));

        send("/order/createMany",many);
    }

    private static void send(String path, Map<String, Object> map) throws NoSuchAlgorithmException, InvalidKeyException {
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
        try {
            Response response = call.execute();

            System.out.println("API RESPONSE: ["+response.message() +"] :" +response.body().string());
        }catch (IOException e){
            e.printStackTrace();
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
     * @param params: Map input parameters
     * @return signature used to be a parameter in the header
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    private static String genGetSign(Map<String, Object> params) throws NoSuchAlgorithmException, InvalidKeyException {
        StringBuilder sb = genQueryStr(params);
        String queryStr = TIMESTAMP + API_KEY + RECV_WINDOW + sb;

        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(API_SECRET, "HmacSHA256");
        sha256_HMAC.init(secret_key);
        return Base64.getEncoder().encodeToString(sha256_HMAC.doFinal(queryStr.getBytes()));
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
    private static StringBuilder genQueryStr(Map<String, Object> map) {
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
        return sb;
    }
}
