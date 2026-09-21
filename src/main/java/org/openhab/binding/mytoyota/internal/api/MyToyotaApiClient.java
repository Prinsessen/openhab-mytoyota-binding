/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mytoyota.internal.api;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client for Toyota Connected Services Europe ("MyToyota" / Toyota One app, also Lexus and Subaru).
 *
 * The API is not public. The flow below is what the mobile app does, as documented by the
 * pytoyoda project (https://github.com/pytoyoda/pytoyoda):
 * <ol>
 * <li>authenticate: POST JSON to the ForgeRock realm, answering the returned callbacks with user name and
 * password until a {@code tokenId} comes back,</li>
 * <li>authorize: GET with that token as cookie, the 302 redirect carries the authorization {@code code},</li>
 * <li>token: POST the code to the access_token endpoint (basic auth of the app's client id), which returns
 * access, refresh and id tokens; the {@code uuid} claim of the id token identifies the user in every
 * later request.</li>
 * </ol>
 * Data requests go to the one-api gateway with the app's api key, the uuid and an HMAC "client ref".
 *
 * @author Nanna Agesen - Initial contribution
 */
@NonNullByDefault
public class MyToyotaApiClient {

    private static final String CLIENT_VERSION = "2.14.0";
    private static final String API_KEY = "tTZipv6liF74PwMfk9Ed68AQ0bISswwf3iHQdqcF";
    private static final String API_BASE_URL = "https://ctpa-oneapi.tceu-ctp-prd.toyotaconnectedeurope.io";
    private static final String LOGIN_BASE = "https://b2c-login.toyota-europe.com";

    public static final String ENDPOINT_VEHICLES = "/v2/vehicle/guid";
    public static final String ENDPOINT_VEHICLE_STATUS = "/v1/vehicle/status";
    public static final String ENDPOINT_ELECTRIC_STATUS = "/v1/global/remote/electric/status";
    public static final String ENDPOINT_ELECTRIC_REALTIME = "/v1/global/remote/electric/realtime-status";
    public static final String ENDPOINT_REFRESH_STATUS = "/v1/remote/status";
    public static final String ENDPOINT_LOCATION = "/v1/location";
    public static final String ENDPOINT_TELEMETRY = "/v3/telemetry";
    public static final String ENDPOINT_HEALTH = "/v1/vehiclehealth/status";
    public static final String ENDPOINT_CLIMATE_STATUS = "/v1/vehicle/climate-status";

    private static final int[] BACKOFF_SECONDS = { 2, 4, 8 };

    private final Logger logger = LoggerFactory.getLogger(MyToyotaApiClient.class);

    private final String username;
    private final String password;
    private final String brand;
    private final String realm;
    private final String clientId;
    private final String redirectUri;
    private final String basicAuth;

    private final HttpClient http;
    private final Object tokenLock = new Object();

    private @Nullable String accessToken;
    private @Nullable String refreshToken;
    private @Nullable String uuid;
    private Instant tokenExpiry = Instant.EPOCH;

    /**
     * @param brand "T" (Toyota), "L" (Lexus) or "S" (Subaru, own realm and client)
     */
    public MyToyotaApiClient(String username, String password, String brand) {
        this.username = username;
        this.password = password;
        this.brand = brand;
        if ("S".equals(brand)) {
            realm = "alliance-subaru";
            clientId = "8c4921b0b08901fef389ce1af49c4e10.subaru.com";
            redirectUri = "com.subaru.oneapp:/oauth2Callback";
            basicAuth = "basic OGM0OTIxYjBiMDg5MDFmZWYzODljZTFhZjQ5YzRlMTAuc3ViYXJ1LmNvbTpJaGNkcjV4YmhIYlRSMk9aOGdRa3YyNTZicmhTYjc=";
        } else {
            realm = "tme";
            clientId = "oneapp";
            redirectUri = "com.toyota.oneapp:/oauth2Callback";
            basicAuth = "basic b25lYXBwOm9uZWFwcA==";
        }
        CookieManager cookies = new CookieManager();
        cookies.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER).cookieHandler(cookies).build();
    }

    // ------------------------------------------------------------------ login

    /** Makes sure a valid access token exists, refreshing or logging in as needed. */
    public void login() throws MyToyotaApiException {
        synchronized (tokenLock) {
            if (accessToken != null && Instant.now().isBefore(tokenExpiry.minusSeconds(60))) {
                return;
            }
            String refresh = refreshToken;
            if (refresh != null) {
                try {
                    refreshTokens(refresh);
                    return;
                } catch (MyToyotaApiException e) {
                    logger.debug("Token refresh failed ({}), logging in again", e.getMessage());
                }
            }
            authenticate();
        }
    }

    private void authenticate() throws MyToyotaApiException {
        String authenticateUrl = LOGIN_BASE + "/json/realms/root/realms/" + realm
                + "/authenticate?authIndexType=service&authIndexValue=oneapp";
        String authorizeUrl = LOGIN_BASE + "/oauth2/realms/root/realms/" + realm + "/authorize?client_id="
                + clientId + "&scope=openid+profile+write&response_type=code&redirect_uri=" + redirectUri
                + "&code_challenge=plain&code_challenge_method=plain";

        // 1. authenticate: answer the callbacks until a tokenId arrives
        JsonObject data = new JsonObject();
        String tokenId = null;
        for (int attempt = 0; attempt < 10 && tokenId == null; attempt++) {
            if (data.has("callbacks")) {
                for (JsonElement cbEl : data.getAsJsonArray("callbacks")) {
                    JsonObject cb = cbEl.getAsJsonObject();
                    String type = str(cb, "type");
                    String prompt = firstValue(cb, "output");
                    if ("NameCallback".equals(type) && "User Name".equals(prompt)) {
                        setFirstInput(cb, username);
                    } else if ("PasswordCallback".equals(type)) {
                        setFirstInput(cb, password);
                    } else if ("TextOutputCallback".equals(type) && "User Not Found".equals(prompt)) {
                        throw new MyToyotaApiException("Login failed: user not found");
                    }
                }
            }
            HttpResponse<String> resp = send(HttpRequest.newBuilder(URI.create(authenticateUrl))
                    .header("content-type", "application/json").timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(data.toString())).build());
            if (resp.statusCode() != 200) {
                throw new MyToyotaApiException("Login failed: authenticate returned " + resp.statusCode() + " "
                        + shortBody(resp.body()));
            }
            data = JsonParser.parseString(resp.body()).getAsJsonObject();
            if (data.has("tokenId")) {
                tokenId = data.get("tokenId").getAsString();
            }
        }
        if (tokenId == null) {
            throw new MyToyotaApiException("Login failed: no tokenId after answering the callbacks");
        }

        // 2. authorize: the redirect carries the code
        HttpResponse<String> auth = send(HttpRequest.newBuilder(URI.create(authorizeUrl))
                .header("cookie", "iPlanetDirectoryPro=" + tokenId).timeout(Duration.ofSeconds(30)).GET().build());
        if (auth.statusCode() != 302) {
            throw new MyToyotaApiException("Login failed: authorize returned " + auth.statusCode());
        }
        String location = auth.headers().firstValue("location").orElse("");
        @Nullable
        String code = location == null ? null : queryParam(location, "code");
        if (code == null) {
            throw new MyToyotaApiException("Login failed: no code in authorize redirect");
        }

        // 3. tokens
        JsonObject tokens = postToken(Map.of("client_id", clientId, "code", code, "redirect_uri", redirectUri,
                "grant_type", "authorization_code", "code_verifier", "plain"));
        storeTokens(tokens);
        logger.debug("Logged in to Toyota Connected Services as uuid {}…", short6(uuid));
    }

    private void refreshTokens(String refresh) throws MyToyotaApiException {
        JsonObject tokens = postToken(Map.of("client_id", clientId, "redirect_uri", redirectUri, "grant_type",
                "refresh_token", "code_verifier", "plain", "refresh_token", refresh));
        storeTokens(tokens);
        logger.debug("Refreshed Toyota tokens");
    }

    private JsonObject postToken(Map<String, String> form) throws MyToyotaApiException {
        String tokenUrl = LOGIN_BASE + "/oauth2/realms/root/realms/" + realm + "/access_token";
        String body = form.entrySet().stream().map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).collect(Collectors.joining("&"));
        HttpResponse<String> resp = send(HttpRequest.newBuilder(URI.create(tokenUrl)).header("authorization", basicAuth)
                .header("content-type", "application/x-www-form-urlencoded").timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
        if (resp.statusCode() != 200) {
            throw new MyToyotaApiException("Token request returned " + resp.statusCode() + " " + shortBody(resp.body()));
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private void storeTokens(JsonObject tokens) throws MyToyotaApiException {
        for (String field : new String[] { "access_token", "id_token", "refresh_token", "expires_in" }) {
            if (!tokens.has(field)) {
                throw new MyToyotaApiException("Token response lacks " + field);
            }
        }
        accessToken = tokens.get("access_token").getAsString();
        refreshToken = tokens.get("refresh_token").getAsString();
        tokenExpiry = Instant.now().plusSeconds(tokens.get("expires_in").getAsLong());
        uuid = jwtClaim(tokens.get("id_token").getAsString(), "uuid");
        if (uuid == null) {
            throw new MyToyotaApiException("id_token carries no uuid claim");
        }
    }

    /** Drops the session so that the next call logs in from scratch. */
    public void invalidate() {
        synchronized (tokenLock) {
            accessToken = null;
            refreshToken = null;
            tokenExpiry = Instant.EPOCH;
        }
    }

    // --------------------------------------------------------------- requests

    public JsonObject get(String endpoint, @Nullable String vin) throws MyToyotaApiException {
        return request("GET", endpoint, vin);
    }

    public JsonObject post(String endpoint, @Nullable String vin) throws MyToyotaApiException {
        return request("POST", endpoint, vin);
    }

    private JsonObject request(String method, String endpoint, @Nullable String vin) throws MyToyotaApiException {
        login();
        HttpResponse<String> resp = null;
        for (int attempt = 0; attempt <= BACKOFF_SECONDS.length; attempt++) {
            resp = send(buildApiRequest(method, endpoint, vin));
            int code = resp.statusCode();
            if (code == 200 || code == 202) {
                String body = resp.body();
                return body.isEmpty() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
            }
            if (code == 401 && attempt == 0) {
                logger.debug("{} {} returned 401, logging in again", method, endpoint);
                invalidate();
                login();
                continue;
            }
            boolean transientError = code == 429 || code >= 500;
            if (!transientError || attempt >= BACKOFF_SECONDS.length) {
                break;
            }
            int wait = BACKOFF_SECONDS[attempt];
            logger.debug("{} {} returned {}, retrying in {} s", method, endpoint, code, wait);
            try {
                Thread.sleep(wait * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new MyToyotaApiException("Interrupted while waiting to retry " + endpoint);
            }
        }
        throw new MyToyotaApiException(method + " " + endpoint + " failed: " + (resp == null ? "no response"
                : resp.statusCode() + " " + shortBody(resp.body())));
    }

    private HttpRequest buildApiRequest(String method, String endpoint, @Nullable String vin)
            throws MyToyotaApiException {
        String token = accessToken;
        String userId = uuid;
        if (token == null || userId == null) {
            throw new MyToyotaApiException("Not logged in");
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(API_BASE_URL + endpoint))
                .timeout(Duration.ofSeconds(60)).header("x-api-key", API_KEY).header("API_KEY", API_KEY)
                .header("x-guid", userId).header("guid", userId).header("x-client-ref", hmacSha256(CLIENT_VERSION, userId))
                .header("x-correlationid", UUID.randomUUID().toString()).header("x-appversion", CLIENT_VERSION)
                .header("x-channel", "ONEAPP").header("x-brand", brand).header("x-region", "EU")
                .header("x-user-region", "EU").header("authorization", "Bearer " + token)
                .header("user-agent", "okhttp/4.10.0").header("accept", "application/json");
        if ("L".equals(brand) || "S".equals(brand)) {
            b.header("x-appbrand", brand).header("brand", brand);
        }
        if (vin != null) {
            b.header("vin", vin);
        }
        if ("POST".equals(method)) {
            b.header("content-type", "application/json").POST(HttpRequest.BodyPublishers.noBody());
        } else {
            b.GET();
        }
        return b.build();
    }

    private HttpResponse<String> send(HttpRequest request) throws MyToyotaApiException {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new MyToyotaApiException("HTTP error on " + request.uri().getPath() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MyToyotaApiException("Interrupted on " + request.uri().getPath());
        }
    }

    // ---------------------------------------------------------------- helpers

    private static String hmacSha256(String key, String message) throws MyToyotaApiException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) {
            throw new MyToyotaApiException("HMAC not available: " + e.getMessage());
        }
    }

    private static @Nullable String jwtClaim(String jwt, String claim) {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        try {
            String json = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonObject payload = JsonParser.parseString(json).getAsJsonObject();
            return payload.has(claim) ? payload.get(claim).getAsString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static @Nullable String queryParam(String url, String name) {
        int q = url.indexOf('?');
        if (q < 0) {
            return null;
        }
        for (String pair : url.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            if (name.equals(key)) {
                return eq < 0 ? "" : java.net.URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static @Nullable String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static @Nullable String firstValue(JsonObject cb, String listName) {
        JsonArray list = cb.getAsJsonArray(listName);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return str(list.get(0).getAsJsonObject(), "value");
    }

    private static void setFirstInput(JsonObject cb, String value) {
        JsonArray input = cb.getAsJsonArray("input");
        if (input != null && !input.isEmpty()) {
            input.get(0).getAsJsonObject().addProperty("value", value);
        }
    }

    private static String shortBody(String body) {
        return body.length() > 200 ? body.substring(0, 200) + "…" : body;
    }

    private static String short6(@Nullable String s) {
        return s == null ? "?" : s.length() > 6 ? s.substring(0, 6) : s;
    }
}
