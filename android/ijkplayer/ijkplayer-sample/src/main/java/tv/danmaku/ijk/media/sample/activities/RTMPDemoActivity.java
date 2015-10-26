package tv.danmaku.ijk.media.sample.activities;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.sample.R;
import tv.danmaku.ijk.media.sample.widget.media.AndroidMediaController;
import tv.danmaku.ijk.media.sample.widget.media.IjkVideoView;
import tv.danmaku.ijk.media.sample.widget.media.MeasureHelper;

/**
 * Created by shinechen on 9/25/15.
 */
public class RTMPDemoActivity extends AppCompatActivity {

    private static final String TAG = "RTMPDemoActivity";

    static private final String VIDEO_LOW = "rtmp://122.147.46.161/edgecast/xAUS1x";
    static private final String VIDEO_MEDIUM = "rtmp://122.147.46.161/edgecast/xAUS1x_500";
    static private final String VIDEO_HIGH = "rtmp://122.147.46.161/edgecast/xAUS1x_700";

//    static private final String VIDEO_MEDIUM = "rtmp://10.10.10.139:1935/live/livestream";

    static private final String [] VIDEO_SOURCES = {VIDEO_LOW, VIDEO_MEDIUM, VIDEO_HIGH};
    static private final long INTERVAL_RANDOM_PLAYBACK = 20000;

    private AndroidMediaController mMediaController;
    private IjkVideoView mVideoView;
    private TextView mToastTextView;
    private TextView mInfoTextView;
    private TextView mBufferingTextView;
    private TextView mFpsTextView;
    private TextView mBandwidthTextView;

    private boolean mBackPressed;

    private static final long PREPARE_TIMEOUT_MILLIS = 6000;
    private long mPrepareStartTime = 0;
    private long mBufferingStartTime = 0;
    private long mBufferingTime = 0;
    private Runnable mPrepareTimeoutMonitor = null;

    private Handler mHandler;
    private int mDataSourceChanges = 0;

    private AtomicBoolean mRandomPlay = new AtomicBoolean(false);
    private Random random = new Random();

    public static Intent newIntent(Context context) {
        Intent intent = new Intent(context, RTMPDemoActivity.class);
        return intent;
    }

    public static void intentTo(Context context) {
        context.startActivity(newIntent(context));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtmp_player);

        // Init UI
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        mMediaController = new AndroidMediaController(this, false);
        mMediaController.setSupportActionBar(actionBar);

        mToastTextView = (TextView) findViewById(R.id.toast_text_view);
        mInfoTextView = (TextView) findViewById(R.id.info_text_view);
        mBufferingTextView = (TextView) findViewById(R.id.buffering_text_view);
        mFpsTextView = (TextView) findViewById(R.id.info_fps);
        mBandwidthTextView = (TextView) findViewById(R.id.info_bandwidth);

        // init player
        IjkMediaPlayer.loadLibrariesOnce(null);
        IjkMediaPlayer.native_profileBegin("libijkplayer.so");

        mVideoView = (IjkVideoView) findViewById(R.id.video_view);
        mVideoView.setMediaController(mMediaController);

        mHandler = new Handler(getMainLooper());

        loadListeners();

        playVideo(VIDEO_MEDIUM);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_rtmp_player, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        mRandomPlay.set(false);

        int id = item.getItemId();
        switch (id) {
            case R.id.action_rtmp_low:
                playVideo(VIDEO_LOW);
                return true;
            case R.id.action_rtmp_medium:
                playVideo(VIDEO_MEDIUM);
                return true;
            case R.id.action_rtmp_high:
                playVideo(VIDEO_HIGH);
                return true;
            case R.id.action_rtmp_random:
                randomPlay();
                return true;
            case R.id.action_toggle_ratio:
                toggleAspectRatio();
                return true;
            case R.id.action_toggle_render:
                toggleRenderer();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean show = super.onPrepareOptionsMenu(menu);
        if (!show)
            return show;

        return true;
    }

    @Override
    public void onBackPressed() {
        mBackPressed = true;

        super.onBackPressed();
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (mBackPressed || !mVideoView.isBackgroundPlayEnabled()) {
            mVideoView.stopPlayback();
            mVideoView.release(true);
            mVideoView.stopBackgroundPlay();
        } else {
            mVideoView.enterBackground();
        }
        IjkMediaPlayer.native_profileEnd();
    }

    private void loadListeners() {
        mVideoView.setOnPreparedListener(new IMediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(IMediaPlayer mp) {
                Log.d(TAG, "onPrepared");

                long prepareTime = System.currentTimeMillis() - mPrepareStartTime;

                StringBuilder sb = new StringBuilder();
                sb.append("Source Changes #");
                sb.append(++mDataSourceChanges).append("\n");
                sb.append("Prepare Completed in ");
                sb.append(prepareTime).append("ms\n");
                sb.append("video size = ").append(mp.getVideoWidth()).append("x").append(mp.getVideoHeight()).append("\n");
                sb.append(mp.getDataSource());
                updateInfo(sb.toString());

                if (mPrepareTimeoutMonitor != null) {
                    mHandler.removeCallbacks(mPrepareTimeoutMonitor);
                    mPrepareTimeoutMonitor = null;
                }

                // Seek
                if (mVideoView.getDuration() < prepareTime) {
                    Log.d(TAG, "seek to: " + prepareTime);
                    mVideoView.seekTo((int) prepareTime);
                }
            }
        });

        mVideoView.setOnInfoListener(new IMediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(IMediaPlayer mp, int what, int extra) {

                switch (what) {
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_START:
                        mBufferingStartTime = System.currentTimeMillis();
                        break;
                    case IMediaPlayer.MEDIA_INFO_BUFFERING_END:
                        mBufferingTime = System.currentTimeMillis() - mBufferingStartTime;
                        mBufferingTextView.setText("Buffering time " + mBufferingTime + " ms");
                        break;
                    case IMediaPlayer.MEDIA_INFO_NETWORK_BANDWIDTH:
                        Log.d(TAG, "onInfo: MEDIA_INFO_NETWORK_BANDWIDTH " + extra);
                        mBandwidthTextView.setText(String.format("Bandwidth %9d Bps", extra));
                        break;
                    case IMediaPlayer.MEDIA_INFO_FPS_UPDATE:
                        mFpsTextView.setText(String.format("FPS %4d", extra));
                        break;
                }
                return false;
            }
        });
    }

    private void playVideo(final String path) {
        String name = path.substring(path.lastIndexOf("/") + 1);
        setTitle(name);

        mPrepareTimeoutMonitor = new Runnable() {
            @Override
            public void run() {
                mHandler.removeCallbacks(this);
                Log.e(TAG, "Prepare timeout!!!");
                Log.e(TAG, "-- Reset data source to media player --");

                updateInfo("Prepare timeout!!!");
                Toast.makeText(RTMPDemoActivity.this, "Prepare timeout!!!", Toast.LENGTH_LONG).show();

                playVideo(path);
            }
        };
        mHandler.postDelayed(mPrepareTimeoutMonitor, PREPARE_TIMEOUT_MILLIS);

        mPrepareStartTime = System.currentTimeMillis();

        mVideoView.setVideoPath(path);
        mVideoView.start();
    }

    private void toggleAspectRatio() {
        int aspectRatio = mVideoView.toggleAspectRatio();
        String aspectRatioText = MeasureHelper.getAspectRatioText(this, aspectRatio);
        showToast(aspectRatioText);
    }

    private void toggleRenderer() {
        int render = mVideoView.toggleRender();
        String renderText = IjkVideoView.getRenderText(this, render);
        showToast(renderText);
    }

    private void showToast(String text) {
        mToastTextView.setText(text);
        mMediaController.showOnce(mToastTextView);
    }

    private void updateInfo(String info) {
        mInfoTextView.setText(info);
    }

    private void appendInfo(String info) {
        mInfoTextView.setText(mInfoTextView.getText() + "\n" + info);
    }

    private void randomPlay() {
        Log.d(TAG, "randomPlay");

        mRandomPlay.set(true);

        String source = VIDEO_SOURCES[random.nextInt(VIDEO_SOURCES.length)];
        playVideo(source);

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (mRandomPlay.get() == false) {
                    return;
                }
                randomPlay();
            }
        }, INTERVAL_RANDOM_PLAYBACK);
    }
}
