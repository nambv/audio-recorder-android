package com.dwarvesf.audio_recorder.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.logging.HttpLoggingInterceptor;

import retrofit.GsonConverterFactory;
import retrofit.Retrofit;
import retrofit.RxJavaCallAdapterFactory;
import retrofit.http.Body;
import retrofit.http.POST;
import rx.Observable;

/**
 * Created by nambv on 3/22/16.
 */
public interface NetworkService {

    class Factory {
        public static NetworkService create() {

            Gson gson = new Gson();

            OkHttpClient client = new OkHttpClient();
            HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
            interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
            client.interceptors().add(interceptor);

            Retrofit retrofit = new Retrofit.Builder()
                    .addCallAdapterFactory(RxJavaCallAdapterFactory.create())
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .baseUrl("http://192.168.31.150:8081")
                    .client(client)
                    .build();
            return retrofit.create(NetworkService.class);
        }
    }

    @POST("v1/file")
    Observable<JsonObject> uploadRecordedFile(@Body RequestBody file);
}
