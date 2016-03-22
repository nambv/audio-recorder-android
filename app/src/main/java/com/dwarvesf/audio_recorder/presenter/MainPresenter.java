package com.dwarvesf.audio_recorder.presenter;

import com.dwarvesf.audio_recorder.R;
import com.dwarvesf.audio_recorder.RecorderApp;
import com.dwarvesf.audio_recorder.presenter.interfaces.IMainPresenter;
import com.dwarvesf.audio_recorder.service.NetworkService;
import com.dwarvesf.audio_recorder.util.Logger;
import com.dwarvesf.audio_recorder.view.mvp.MainMvpView;
import com.google.gson.JsonObject;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.RequestBody;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;

import retrofit.HttpException;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by nambv on 3/22/16.
 */
public class MainPresenter implements IMainPresenter {

    MainMvpView mView;
    Subscription mSubscription;
    int fileId = 0;

    @Override
    public void attachView(MainMvpView view) {
        mView = view;
    }

    @Override
    public void uploadRecordedFile(File file) {

        // Show uploading message first
        mView.showMessage(R.string.uploading_record);

        if (mSubscription != null) mSubscription.unsubscribe();

        final RecorderApp randomGirlApp = RecorderApp.get(mView.getContext());
        final NetworkService networkService = randomGirlApp.getNetworkService();

        // Create request body
        try {
            InputStream in = new FileInputStream(file);
            byte[] buf;
            buf = new byte[in.available()];
            while (in.read(buf) != -1) ;
            RequestBody requestBody = RequestBody.create(MediaType.parse("application/octet-stream"), buf);

            mSubscription = networkService.uploadRecordedFile(requestBody)
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(randomGirlApp.defaultSubscribeScheduler())
                    .subscribe(new Subscriber<JsonObject>() {
                        @Override
                        public void onCompleted() {
                            Logger.w("onCompleted");

                            if (fileId != 0) {

                                // Alert success message
                                mView.showMessage(R.string.upload_record_done);

                                // Call this method to play uploaded file
                                mView.playAudioAfterUploaded(fileId);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            Logger.w("onError: " + e.getMessage());

                            if (e instanceof ConnectException)
                                // Connection problem, may be server can't not be reach, device is in offline mode...
                                mView.showMessage(R.string.cant_connect_to_server);
                            else if (e instanceof HttpException) {
                                switch (((HttpException) e).code()) {
                                    case 400: // Bad request
                                        mView.showMessage(R.string.upload_problem);
                                        break;
                                    case 404: // Server not found
                                        mView.showMessage(R.string.server_not_found);
                                }
                            }
                        }

                        @Override
                        public void onNext(JsonObject result) {

                            if (!result.isJsonNull()) {
                                // Set fileId if upload file successfully
                                fileId = result.get("file").getAsJsonObject().get("ID").getAsInt();
                                Logger.w("fileId: " + fileId);
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void detachView() {
        mView = null;
    }
}
