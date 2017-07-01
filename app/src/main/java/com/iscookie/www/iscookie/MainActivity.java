/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.iscookie.www.iscookie;

import android.app.Dialog;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.flurgle.camerakit.CameraListener;
import com.flurgle.camerakit.CameraView;
import com.github.jinatonic.confetti.CommonConfetti;
import com.github.jinatonic.confetti.ConfettiManager;
import com.github.ybq.android.spinkit.SpinKitView;
import com.iscookie.www.iscookie.activities.helper.ConfettiActivity;
import com.iscookie.www.iscookie.utils.ImageUtils;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements ConfettiActivity {

    private static final int INPUT_SIZE = 224;
    private static final int IMAGE_MEAN = 117;
    private static final float IMAGE_STD = 1;
    private static final String INPUT_NAME = "input";
    private static final String OUTPUT_NAME = "output";

    private static final float CONFIDENCE_THRESHOLD = .3f;

    private static final String MODEL_FILE = "file:///android_asset/tensorflow_inception_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/imagenet_comp_graph_label_strings.txt";

    private Classifier classifier;
    private Executor executor = Executors.newSingleThreadExecutor();
    private TextView textViewResult;
    private Button btnDetectObject;
    private Button btnToggleCamera;
    private SpinKitView loadingSpinner;
    private ImageView imageViewResult;
    private CameraView cameraView;

    private SoundPool soundPool;
    private boolean poolReady = false;

    private int dingId;
    private int booId;

    private Dialog loadingDialog;

    private void playSound(final int soundId) {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        float actualVolume = (float) audioManager
                .getStreamVolume(AudioManager.STREAM_MUSIC);
        float maxVolume = (float) audioManager
                .getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        float volume = actualVolume / maxVolume;
        if (poolReady) {
            soundPool.play(soundId, volume, volume, 1, 0, 1f);
        } else {
            Timber.e("Sound file for id " + soundId + " was not ready to be played");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setUpConfetti();

        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 1);
        soundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId,
                    int status) {
                poolReady = true;
            }
        });
        dingId = soundPool.load(this, R.raw.elevator, 1);
        booId = soundPool.load(this, R.raw.booing, 1);

        cameraView = (CameraView) findViewById(R.id.cameraView);
        imageViewResult = (ImageView) findViewById(R.id.imageViewResult);
        textViewResult = (TextView) findViewById(R.id.textViewResult);
        textViewResult.setMovementMethod(new ScrollingMovementMethod());

        btnToggleCamera = (Button) findViewById(R.id.btnToggleCamera);
        btnDetectObject = (Button) findViewById(R.id.btnDetectObject);
        loadingSpinner = (SpinKitView) findViewById(R.id.loadingSpinner);

        loadingSpinner.setVisibility(View.VISIBLE);
        btnDetectObject.setVisibility(View.GONE);

        cameraView.setCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(byte[] picture) {
                showLoadingDialog();

                super.onPictureTaken(picture);

                new ClassifyImageTask().execute(picture);
            }
        });

        btnToggleCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.toggleFacing();
            }
        });
        btnDetectObject.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraView.captureImage();
            }
        });

        initTensorFlowAndLoadModel();
    }

    private void showLoadingDialog() {
        loadingDialog = new MaterialDialog.Builder(MainActivity.this)
                .title(R.string.loading)
                .content(ImageUtils.getRandomLoadingMessage())
                .progress(true, 0).show();
    }

    private void hideLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private void showResultOverlay(final List<Classifier.Recognition> results) {
        textViewResult.setText(results.toString());
        if (!results.isEmpty()) {
            for (Classifier.Recognition result : results) {
                if (result.getConfidence() > CONFIDENCE_THRESHOLD) {
                    // Show positive message/overlay to the user.
                    makeToast("COOKIE");
                    generateOnce().animate();
                    playSound(dingId);
                    return;
                }
            }

            makeToast("Not sure what this is");
            playSound(booId);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.start();
    }

    @Override
    protected void onPause() {
        cameraView.stop();
        hideLoadingDialog();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideLoadingDialog();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                classifier.close();
            }
        });
    }

    private void initTensorFlowAndLoadModel() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    classifier = TensorFlowImageClassifier.create(
                            getAssets(),
                            MODEL_FILE,
                            LABEL_FILE,
                            INPUT_SIZE,
                            IMAGE_MEAN,
                            IMAGE_STD,
                            INPUT_NAME,
                            OUTPUT_NAME);
                    doneLoadingClassifier();
                } catch (final Exception e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            makeToast(getString(R.string.classifier_error));
                            Timber.e("Error creating classifier: " + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void makeToast(final String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    private void doneLoadingClassifier() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Timber.d("done loading classifier");
                loadingSpinner.setVisibility(View.GONE);
                btnDetectObject.setVisibility(View.VISIBLE);
            }
        });
    }

    // ** Show or Hide the share button overlay on the current view.

    private void showShareView() {
        Timber.d("showShareView");
    }

    private void hideShareView() {
        Timber.d("hideShareView");
    }

    // ** Confetti Activity Logic Below ** //

    private void setUpConfetti() {
        Timber.d("setUpConfetti");
        container = (ViewGroup) findViewById(R.id.container);
        final Resources res = getResources();
        goldDark = res.getColor(R.color.gold_dark);
        goldMed = res.getColor(R.color.gold_med);
        gold = res.getColor(R.color.gold);
        goldLight = res.getColor(R.color.gold_light);
        colors = new int[]{goldDark, goldMed, gold, goldLight};
    }

    protected ViewGroup container;
    protected int goldDark, goldMed, gold, goldLight;
    private int[] colors;

    @Override
    public ConfettiManager generateOnce() {
        return CommonConfetti.rainingConfetti(container, colors)
                .oneShot();
    }

    @Override
    public ConfettiManager generateStream() {
        return CommonConfetti.rainingConfetti(container, colors)
                .stream(3000);
    }

    @Override
    public ConfettiManager generateInfinite() {
        return CommonConfetti.rainingConfetti(container, colors)
                .infinite();
    }

    private class ClassifyImageTask extends AsyncTask<byte[], Integer, Long> {

        private Bitmap scaledBitmap;
        private List<Classifier.Recognition> results;

        protected Long doInBackground(byte[]... pictures) {
            int count = pictures.length;
            Timber.d("ClassifyImageTask with " + count + " byte array params (should be 1)");
            final byte[] picture = pictures[0];

            Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);

            results = classifier.recognizeImage(bitmap);
            return 1L;
        }

        protected void onPostExecute(Long exitCode) {
            imageViewResult.setImageBitmap(scaledBitmap);
            showResultOverlay(results);
            hideLoadingDialog();
        }
    }
}
