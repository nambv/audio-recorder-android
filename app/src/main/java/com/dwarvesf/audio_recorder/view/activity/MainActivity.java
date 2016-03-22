package com.dwarvesf.audio_recorder.view.activity;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dwarvesf.audio_recorder.R;
import com.dwarvesf.audio_recorder.presenter.MainPresenter;
import com.dwarvesf.audio_recorder.presenter.interfaces.IMainPresenter;
import com.dwarvesf.audio_recorder.util.Logger;
import com.dwarvesf.audio_recorder.util.RecordConstant;
import com.dwarvesf.audio_recorder.util.Util;
import com.dwarvesf.audio_recorder.view.mvp.MainMvpView;

import java.io.File;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import at.markushi.ui.CircleButton;

public class MainActivity extends AppCompatActivity implements MainMvpView, View.OnClickListener, View.OnTouchListener, MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnCompletionListener {

    // Views declaration
    ProgressBar mProgressBar;
    TextView mRecordInstructionTextView;
    TextView mTimerTextView;
    CircleButton mRecordButton;
    SeekBar mSeekBarProgress;
    ImageButton mPlayPauseButton;
    TextView mDurationTextView;

    private MediaRecorder mRecorder = null;
    private MediaPlayer mPlayer = null;

    // We need to use a flag to prevent recorder call stop() many times, may cause app crash
    private boolean mIsRecorderStopped = false;

    // Use to count up timer
    private Timer mTimer;

    // Declare presenter to handle business logic
    private IMainPresenter mPresenter;

    /**
     * Variables use for timer count up
     * 0L means the number zero of type long
     */
    long startTime = 0L;
    long timeInMilliseconds = 0L;
    int secs = 0;
    int mins = 0;
    int milliseconds = 0;
    int mCurrentLenght = 0;
    int mediaFileLengthInMilliseconds = 0; // this value contains the song duration in milliseconds. Look at getDuration() method in MediaPlayer class
    Handler mHandler = new Handler();

    private String mFilePath;

    // Use toast to alert message
    private Toast mToast;

    private String mUrl;

    /**
     * Called when the activity is first created.
     **/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize mPresenter
        mPresenter = new MainPresenter();
        mPresenter.attachView(this);

        // Get views by Id
        mProgressBar = (ProgressBar) findViewById(R.id.pb_loading);
        mRecordInstructionTextView = (TextView) findViewById(R.id.tv_record_instruction);
        mTimerTextView = (TextView) findViewById(R.id.tv_timer);
        mRecordButton = (CircleButton) findViewById(R.id.btn_record);
        mSeekBarProgress = (SeekBar) findViewById(R.id.seek_bar);
        mPlayPauseButton = (ImageButton) findViewById(R.id.btn_play_pause);
        mDurationTextView = (TextView) findViewById(R.id.tv_duration);

        mSeekBarProgress.setMax(99); // It means 100% .0-99

        // Set on touch listener for record button and seek bar
        mSeekBarProgress.setOnTouchListener(this);
        mRecordButton.setOnTouchListener(this);

        // Set default status button play pause is false
        updateButtonPlayPauseStatus(false);

        // Set onClick listener for button
        mPlayPauseButton.setOnClickListener(this);

        // Initialize media player
        setUpMediaPlayer();
    }

    /**
     * This method will initialize media player
     */
    private void setUpMediaPlayer() {

        mPlayer = new MediaPlayer();
        mPlayer.setOnBufferingUpdateListener(this);
        mPlayer.setOnCompletionListener(this);
    }

    @UiThread
    private void showTimerTextView() {
        mRecordInstructionTextView.setVisibility(View.GONE);
        mTimerTextView.setVisibility(View.VISIBLE);
    }

    @UiThread
    private void hideTimerTextView() {
        mRecordInstructionTextView.setVisibility(View.VISIBLE);
        mTimerTextView.setVisibility(View.GONE);
    }

    /**
     * Set default clickable and enable for button play pause is false
     */
    private void updateButtonPlayPauseStatus(boolean enabled) {
        Util.setImageButtonEnabled(getContext(), enabled, mPlayPauseButton, R.drawable.ic_play);
        mPlayPauseButton.setClickable(enabled);
    }

    /**
     * Method which updates the SeekBar primary progress by current song playing position
     */
    private void primarySeekBarProgressUpdater() {
        mSeekBarProgress.setProgress((int) (((float) mPlayer.getCurrentPosition() / mediaFileLengthInMilliseconds) * 100)); // This math construction give a percentage of "was playing"/"song length"
        if (mPlayer.isPlaying()) {
            Runnable notification = new Runnable() {
                public void run() {
                    primarySeekBarProgressUpdater();
                }
            };
            mHandler.postDelayed(notification, 1000);
        }
    }

    private void stopTimerTask() {

        // Call two below method to stop timer
        mTimer.cancel();
        mTimer.purge();
    }

    /**
     * Start count time inside handler thread
     */
    @UiThread
    private void startCountUpTimer() {

        // Set start time
        startTime = SystemClock.uptimeMillis();

        /**
         * Run the runnable updateTimer without delay
         * params: runnable updateTimer
         * params: delay: 0 milliseconds
         */
        mHandler.postDelayed(updateTimer, 0);
    }

    @UiThread
    private void stopCountUpTimer() {
        startTime = 0L;
        timeInMilliseconds = 0L;
        secs = 0;
        mins = 0;
        milliseconds = 0;
        mHandler.removeCallbacks(updateTimer);
        mTimerTextView.setText("00:00");
    }

    /**
     * Setup and start recording
     */
    @UiThread
    private void startRecording() {

        // Initial recorder and set audio source
        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        // Set file name before record
        mFilePath = getFilePath();

        // Build configuration for mRecorder
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setOutputFile(mFilePath);
        mRecorder.setOnErrorListener(errorListener);

        try {
            mRecorder.prepare();

            // Recording is now started
            mRecorder.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method is implemented to tell recorder to stop and release resource associated with this MediaRecorder object
     * May cause RuntimeException, so we should try catch it
     */
    @UiThread
    private void stopRecording() {
        try {
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
    }

    /**
     * This method will update timer texr inside textview in UI thread
     */
    @UiThread
    private void updateTimerTextView() {
        mTimerTextView.setText(getString(R.string.time_format, mins, secs));
    }

    /**
     * Update progress bar when timer is counting
     */
    void updateProgress() {
        mProgressBar.setProgress(mProgressBar.getProgress() + 1);
    }

    /**
     * Clear and reset progress bar
     */
    @UiThread
    void clearProgress() {
        mProgressBar.setProgress(0);
        mProgressBar.clearAnimation();
    }

    private String getFilePath() {

        // Define directory path to store a media recorder
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath, RecordConstant.AUDIO_RECORDER_FOLDER);

        // If directory is not existed, create it first
        if (!file.exists())
            file.mkdirs();

        // Return recorder file location with extension is ".MP3"
        return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + RecordConstant.AUDIO_RECORDER_FILE_EXT_MP3);
    }

    private void uploadRecordedFile() {

        File file = new File(mFilePath);
        if (file.exists()) {
            mPresenter.uploadRecordedFile(file);
        }
    }

    @Override
    protected void onDestroy() {
        mTimer.cancel();
        mTimer.purge();
        mTimer = null;

        mPresenter.detachView();
        super.onDestroy();
    }

    /**
     * Handle error when MediaRecorder has any problem
     */
    private MediaRecorder.OnErrorListener errorListener = new MediaRecorder.OnErrorListener() {
        @Override
        public void onError(MediaRecorder mr, int what, int extra) {
            Logger.e("Error: " + what + ", " + extra);
        }
    };

    private Runnable updateTimer = new Runnable() {
        public void run() {

            // Calculate time duration in milliseconds and convert it into second(s) and minute(s)
            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            secs = (int) (timeInMilliseconds / 1000);
            mins = secs / 60;
            secs = secs % 60;
            milliseconds = (int) (timeInMilliseconds % 1000);
            mHandler.postDelayed(this, 0);

            // Update views
            updateTimerTextView();

            // If enough 30 second, stop it
            if (secs == 30) {
                Logger.w("Stop recording");

                // Hide timer textview
                hideTimerTextView();

                // All method relevant with event stop recording should handle in UI thread
                stopCountUpTimer();
                stopRecording();
                stopTimerTask();
                clearProgress();

                uploadRecordedFile();

                // Set flag is true
                mIsRecorderStopped = true;
            }
        }
    };

    @Override
    public Context getContext() {
        return MainActivity.this;
    }

    @Override
    public void playAudioAfterUploaded(int fileId) {

        // Set fileId to url
        mUrl = "http://161.202.181.42:8081/v1/file/" + fileId;

        try {
            // Set audio type is stream music and set data source by URL file
            mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mPlayer.setDataSource(mUrl);

            mPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Update status button is 'true', so we can click to play audio file from URL
        updateButtonPlayPauseStatus(true);
    }

    @Override
    public void showMessage(String message) {
        if (mToast != null)
            mToast.cancel();
        mToast = Toast.makeText(getContext(), message, Toast.LENGTH_LONG);
        mToast.show();
    }

    @Override
    public void showMessage(int messageId) {
        if (mToast != null)
            mToast.cancel();
        mToast = Toast.makeText(getContext(), messageId, Toast.LENGTH_LONG);
        mToast.show();
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_play_pause) {

//            // Prevent click again
//            updateButtonPlayPauseStatus(false);

            // Set the audio file length in milliseconds from URL
            mediaFileLengthInMilliseconds = mPlayer.getDuration();
            int seconds = mediaFileLengthInMilliseconds / 1000;
            seconds = seconds % 60;
            mDurationTextView.setText(getString(R.string.duration, seconds));

            // Play stream music
            if (!mPlayer.isPlaying()) {
                mPlayer.start();
                mPlayer.seekTo(mCurrentLenght);
                mPlayPauseButton.setImageResource(R.drawable.ic_pause);
            } else {
                mPlayer.pause();
                mCurrentLenght = mPlayer.getCurrentPosition();
                mPlayPauseButton.setImageResource(R.drawable.ic_play);
            }

            primarySeekBarProgressUpdater();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {

        switch (v.getId()) {
            case R.id.btn_record:
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:

                        // Stop media player before recording
                        if (mPlayer.isPlaying()) {
                            mPlayer.stop();
                            mPlayer.reset();

                            updateButtonPlayPauseStatus(false);
                            clearProgress();
                        }

                        // Show timer textview
                        showTimerTextView();

                        // Do not allow button play pause to click
                        updateButtonPlayPauseStatus(false);

                        Logger.w("Start recording");
                        startCountUpTimer();
                        startRecording();

                        /**
                         * Define mTimer and schedule it to run in a period with duration is 30 seconds
                         */
                        mTimer = new Timer();
                        mTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        updateProgress();
                                    }
                                });
                            }
                        }, 1, 30);

                        break;
                    case MotionEvent.ACTION_UP:

                        if (!mIsRecorderStopped) {
                            Logger.w("Stop recording");

                            // Hide timer textview
                            hideTimerTextView();

                            // Stop all current working tasks
                            stopCountUpTimer();
                            stopRecording();
                            stopTimerTask();
                            clearProgress();

                            // Call this method to upload file
                            uploadRecordedFile();
                        }
                        break;
                }
                break;
            case R.id.seek_bar:

                /** Seek bar onTouch event handler. Method which seeks MediaPlayer to seekBar primary progress position*/
                if (mPlayer.isPlaying()) {
                    SeekBar sb = (SeekBar) v;
                    int playPositionInMilliSeconds = (mediaFileLengthInMilliseconds / 100) * sb.getProgress();
                    mPlayer.start();
                    mPlayer.seekTo(playPositionInMilliSeconds);
                }
                break;
        }

        return false;
    }

    /**
     * Method which updates the SeekBar secondary progress by current song loading from URL position
     *
     * @param mp
     * @param percent
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        mSeekBarProgress.setSecondaryProgress(percent);
    }

    /**
     * MediaPlayer onCompletion event handler. Method which calls then song playing is complete
     *
     * @param mp
     */
    @Override
    public void onCompletion(MediaPlayer mp) {

        Logger.w("onCompletion");

        // Reset state media player so we can set data source again to play
        mPlayer.reset();
        mCurrentLenght = 0;
        mDurationTextView.setText("00:00");

        // Set button clickable
        updateButtonPlayPauseStatus(true);
    }
}