package org.elasticsearch.plugin.readonlyrest.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


public class JsonUtil {

    public static <T> T convertJson2Object(String json, Class<T> clazz) {
        return (new Gson()).fromJson(json, clazz);
    }

    public static String convertObject2Json(Object bean) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(bean);
    }

}
