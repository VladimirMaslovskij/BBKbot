package io.proj3ct.TestBlaBlaBot.config;

import org.json.JSONArray;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class MyGeocoder {
    public String city;
    public String street;

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public  String sendGeo(double longitude, double latitude) throws IOException {

        String apiKey = "4db7b23a-c283-4c90-b507-fa77d8b7cf5a";
        String lang = "ru_RU";
        String geocode = longitude + "," + latitude;
        String url = "https://geocode-maps.yandex.ru/1.x/?apikey=" + apiKey +
                "&geocode=" + geocode +
                "&lang" + lang +
                "&format=json";

        JsonReader reader = new JsonReader();
        org.json.JSONObject object = reader.read(url);
        String coords = (String) object.getJSONObject("response").getJSONObject("GeoObjectCollection")
                .getJSONObject("metaDataProperty").getJSONObject("GeocoderResponseMetaData")
                .getJSONObject("Point").get("pos");
        JSONArray address = object.getJSONObject("response").getJSONObject("GeoObjectCollection")
                .getJSONArray("featureMember").getJSONObject(0).getJSONObject("GeoObject")
                .getJSONObject("metaDataProperty").getJSONObject("GeocoderMetaData")
                .getJSONObject("Address").getJSONArray("Components");
//        String country = (String) address.getJSONObject(0).get("name");
        String city = (String) address.getJSONObject(3).get("name");
        setCity(city);
        String street = (String) address.getJSONObject(4).get("name");
        setStreet(street);
//        String house = (String) address.getJSONObject(5).get("name");
        String result = city + ", " + street;
        return result;
    }
}
