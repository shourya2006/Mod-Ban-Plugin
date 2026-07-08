package com.serverside.anticheat.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ModrinthFetcher {
    private static final String API_BASE = "https://api.modrinth.com/v2";

    public static String fetchTranslationKey(String modId) {
        try {
            // 1. Fetch latest version URL
            URL versionUrl = new URL(API_BASE + "/project/" + modId + "/version");
            HttpURLConnection conn = (HttpURLConnection) versionUrl.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "ArcLgenD/ServerSideAntiCheat");
            
            if (conn.getResponseCode() != 200) {
                return null;
            }

            InputStreamReader reader = new InputStreamReader(conn.getInputStream());
            JsonArray versions = JsonParser.parseReader(reader).getAsJsonArray();
            reader.close();

            if (versions.isEmpty()) return null;
            
            // Get the primary file download URL
            JsonObject latestVersion = versions.get(0).getAsJsonObject();
            JsonArray files = latestVersion.getAsJsonArray("files");
            String downloadUrl = null;
            for (JsonElement fileElem : files) {
                JsonObject fileObj = fileElem.getAsJsonObject();
                if (fileObj.has("primary") && fileObj.get("primary").getAsBoolean()) {
                    downloadUrl = fileObj.get("url").getAsString();
                    break;
                }
            }
            
            if (downloadUrl == null && !files.isEmpty()) {
                downloadUrl = files.get(0).getAsJsonObject().get("url").getAsString();
            }
            
            if (downloadUrl == null) return null;

            // 2. Stream the JAR and look for en_us.json
            URL jarUrl = new URL(downloadUrl);
            HttpURLConnection jarConn = (HttpURLConnection) jarUrl.openConnection();
            jarConn.setRequestMethod("GET");
            jarConn.setRequestProperty("User-Agent", "ArcLgenD/ServerSideAntiCheat");
            
            ZipInputStream zis = new ZipInputStream(jarConn.getInputStream());
            ZipEntry entry;
            String foundKey = null;
            
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().matches("assets/.+/en_us\\.json")) {
                    // Found the language file, extract a key
                    Scanner scanner = new Scanner(zis, StandardCharsets.UTF_8).useDelimiter("\\A");
                    if (scanner.hasNext()) {
                        String jsonContent = scanner.next();
                        try {
                            JsonObject langJson = JsonParser.parseString(jsonContent).getAsJsonObject();
                            for (String key : langJson.keySet()) {
                                if (key.length() > 5 && key.length() < 45) {
                                    foundKey = key;
                                    break;
                                }
                            }
                            if (foundKey == null && !langJson.keySet().isEmpty()) {
                                foundKey = langJson.keySet().iterator().next();
                            }
                        } catch (Exception e) {
                            // Fallback to regex if it's malformed JSON
                            Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(jsonContent);
                            if (m.find()) {
                                foundKey = m.group(1);
                            }
                        }
                    }
                    break; // Stop streaming once we found it
                }
            }
            
            zis.close();
            jarConn.disconnect();
            
            return foundKey;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
