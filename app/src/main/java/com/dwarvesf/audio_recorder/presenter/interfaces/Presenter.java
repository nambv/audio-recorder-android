package com.dwarvesf.audio_recorder.presenter.interfaces;

/**
 * Created by nambv on 3/22/16.
 */
public interface Presenter<V> {

    void attachView(V view);
    void detachView();
}
