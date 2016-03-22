package com.dwarvesf.audio_recorder.view.mvp;

/**
 * Created by nambv on 3/22/16.
 */
public interface MainMvpView extends MvpView {

    void playAudioAfterUploaded(int id);

    void showMessage(String message);

    void showMessage(int messageId);
}
