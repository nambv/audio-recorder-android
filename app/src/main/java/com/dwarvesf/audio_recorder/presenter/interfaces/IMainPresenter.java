package com.dwarvesf.audio_recorder.presenter.interfaces;

import com.dwarvesf.audio_recorder.view.mvp.MainMvpView;

import java.io.File;

/**
 * Created by nambv on 3/22/16.
 */
public interface IMainPresenter extends Presenter<MainMvpView> {

    void uploadRecordedFile(File file);
}
