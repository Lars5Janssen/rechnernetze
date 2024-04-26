package config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;

import static syslog.Syslog.syslog;

public class Config {

    private String password;
    private int maxClients;
    private int port;
    private String disconnectMSG;
    private long timeToShutdown;

    public String getPassword() {
        return password;
    }
    public long getTimeToShutdown() { return timeToShutdown; }
    public int getMaxClients() {
        return maxClients;
    }
    public int getPort() {
        return port;
    }
    public String getDisconnectMSG() {return disconnectMSG;}

    public Config readConfigFromFile(String fileName) {
        try (Reader reader = new FileReader(fileName)) {
            Gson gson = new Gson();
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            return gson.fromJson(jsonObject, Config.class);
        } catch (IOException e) {
            syslog(1,1,"File konnte nicht gelesen werden");
            return null;
        }
    }
}