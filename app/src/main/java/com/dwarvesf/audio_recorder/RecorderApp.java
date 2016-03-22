package com.dwarvesf.audio_recorder;

import android.app.Application;
import android.content.Context;

import com.dwarvesf.audio_recorder.service.NetworkService;

import rx.Scheduler;
import rx.schedulers.Schedulers;

/**
 * Created by nambv on 3/22/16.
 */
public class RecorderApp extends Application {

    private NetworkService networkService;
    private Scheduler defaultSubscribeScheduler;

    public NetworkService getNetworkService() {
        if (networkService == null)
            networkService = NetworkService.Factory.create();
        return networkService;
    }

    public static RecorderApp get(Context context) {
        return (RecorderApp) context.getApplicationContext();
    }

    public Scheduler defaultSubscribeScheduler() {
        if (defaultSubscribeScheduler == null) {
            // Get available thread
            defaultSubscribeScheduler = Schedulers.io();
        }
        return defaultSubscribeScheduler;
    }
}
