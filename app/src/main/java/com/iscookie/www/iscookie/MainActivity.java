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
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.SoundPool.OnLoadCompleteListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.flurgle.camerakit.CameraListener;
import com.flurgle.camerakit.CameraView;
import com.github.jinatonic.confetti.CommonConfetti;
import com.github.jinatonic.confetti.ConfettiManager;
import com.github.johnpersano.supertoasts.library.Style;
import com.github.johnpersano.supertoasts.library.SuperActivityToast;
import com.github.ybq.android.spinkit.SpinKitView;
import com.iscookie.www.iscookie.activities.helper.ConfettiActivity;
import com.iscookie.www.iscookie.utils.ClassificationTaskResult;
import com.iscookie.www.iscookie.utils.Classifier;
import com.iscookie.www.iscookie.utils.Classifier.Recognition;
import com.iscookie.www.iscookie.utils.ImageUtils;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import mehdi.sakout.fancybuttons.FancyButton;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements ConfettiActivity {

    private static final int INPUT_SIZE = 299; //224;
    private static final int IMAGE_MEAN = 128; //117;
    private static final float IMAGE_STD = 128; //1;
    private static final String INPUT_NAME = "Mul";
    private static final String OUTPUT_NAME = "final_result";

    private static final float CONFIDENCE_THRESHOLD = .3f;

    private static final String MODEL_FILE = "file:///android_asset/stripped_retrained_graph.pb"; //tensorflow_inception_graph.pb";
    private static final String LABEL_FILE = "file:///android_asset/retrained_labels.txt"; // imagenet_comp_graph_label_strings.txt";

    private static Classifier classifier = null; // Tensorflow classifier.
    private Executor executor = Executors.newSingleThreadExecutor();

    private static Bitmap lastScreenShot;

    // Main layout views.
    private ViewGroup mainContainer;
    private LinearLayout resultLayout;
    private LinearLayout cameraActionLayout;

    // Scene components.
    private TextView textViewResult;
    private Button btnDetectObject;
    private Button btnToggleCamera;
    private SpinKitView loadingTFSpinner;
    private ImageView imageViewResult;
    private CameraView cameraView;

    private FancyButton shareButton;

    // TODO: implement muting and unmuting.
    private ImageView topVolumeView;

    private boolean isMuted = true;

    private SoundPool soundPool;
    private boolean poolReady = false;

    private int booId;
    private int cheeringId;

    private Dialog loadingDialog;

    private AudioManager audioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Setup activity helper classes.
        setUpConfetti();
        setUpSoundpool();

        setContentView(R.layout.activity_main);
        Timber.d("onCreate, after setContentView");

        mainContainer = (ViewGroup) findViewById(R.id.container);
        cameraView = (CameraView) findViewById(R.id.cameraView);
        // cameraView.refreshDrawableState();

        topVolumeView = (ImageView) findViewById(R.id.topVolumeView);
//        topVolumeView.setImageDrawable(); // set volume mute/unmute image based on initial mutesetting.
        topVolumeView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Timber.d("muted: " + isMuted);
                isMuted = !isMuted;
                // TODO: change the mute logo to muted or unmuted.
            }
        });

        cameraActionLayout = (LinearLayout) findViewById(R.id.cameraActionLayout);

        // Classification result view items.
        resultLayout = (LinearLayout) findViewById(R.id.resultLayout);
        imageViewResult = (ImageView) findViewById(R.id.imageViewResult);
        textViewResult = (TextView) findViewById(R.id.textViewResult);
        textViewResult.setMovementMethod(new ScrollingMovementMethod());
        shareButton = (FancyButton) findViewById(R.id.shareButton);

        btnToggleCamera = (Button) findViewById(R.id.btnToggleCamera);
        btnDetectObject = (Button) findViewById(R.id.btnDetectObject);
        loadingTFSpinner = (SpinKitView) findViewById(R.id.loadingSpinner);

        btnDetectObject.setVisibility(View.GONE);

        cameraView.setCameraListener(new CameraListener() {
            @Override
            public void onPictureTaken(byte[] picture) {
                showLoadingDialog();
                super.onPictureTaken(picture);

                Timber.d("starting ClassifyImageTask with picture length " + picture.length);
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
                try {
                    cameraView.captureImage();
                } catch (Exception e) {
                    makeToast(getString(R.string.camera_error));
                    // Attempt to restart the camera after a failed image retrieval.
                    cameraView.stop();
                    cameraView.start();
                }
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

    private Recognition renderClassificationResultDisplay(final List<Recognition> results) {
        textViewResult.setText(results.toString());
        for (Recognition result : results) {
            if (result.getConfidence() > CONFIDENCE_THRESHOLD) {
                // Show positive message/overlay to the user.
                makeToast(getString(R.string.target_item) + "!");
                generateOnce().animate();
                playSound(cheeringId);
                return result;
            }
        }

        if (results.isEmpty()) {
            makeToast(getString(R.string.empty_result_message));
        } else {
            makeToast("Not a " + getString(R.string.target_item));
        }
        playSound(booId);
        // didn't successfully find the app object (current target: cookie).
        return null;
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
                if (classifier != null) {
                    classifier.close();
                    classifier = null;
                }
            }
        });
    }

    private void initTensorFlowAndLoadModel() {
        if (classifier != null) {
            btnDetectObject.setVisibility(View.VISIBLE);
            return;
        }

        // Show the loading spinner for the tensorflow model.
        loadingTFSpinner.setVisibility(View.VISIBLE);
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
                            Timber.e("Error creating classifier: " + e.toString());
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
                loadingTFSpinner.setVisibility(View.GONE);
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

    private void playSound(final int soundId) {
        final float actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        final float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        final float volume = actualVolume / maxVolume;
        if (poolReady) {
            soundPool.play(soundId, volume, volume, 1, 0, 1f);
        } else {
            Timber.e("Sound file for id " + soundId + " was not ready to be played");
        }
    }

    private void setUpSoundpool() {
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 1);
        soundPool.setOnLoadCompleteListener(new OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId,
                    int status) {
                poolReady = true;
            }
        });
//        dingId = soundPool.load(this, R.raw.elevator, 1);
        booId = soundPool.load(this, R.raw.booing, 1);
        cheeringId = soundPool.load(this, R.raw.cheering, 1);
    }

    // ** Confetti Activity Logic Below ** //

    private void setUpConfetti() {
        Timber.d("setUpConfetti");
        final Resources res = getResources();
        goldDark = res.getColor(R.color.gold_dark);
        goldMed = res.getColor(R.color.gold_med);
        gold = res.getColor(R.color.gold);
        goldLight = res.getColor(R.color.gold_light);
        colors = new int[]{goldDark, goldMed, gold, goldLight};
    }

    protected int goldDark, goldMed, gold, goldLight;
    private int[] colors;

    @Override
    public ConfettiManager generateOnce() {
        return CommonConfetti.rainingConfetti(mainContainer, colors)
                .oneShot();
    }

    @Override
    public ConfettiManager generateStream() {
        return CommonConfetti.rainingConfetti(mainContainer, colors)
                .stream(3000);
    }

    @Override
    public ConfettiManager generateInfinite() {
        return CommonConfetti.rainingConfetti(mainContainer, colors)
                .infinite();
    }

    private class ClassifyImageTask extends AsyncTask<byte[], Integer, ClassificationTaskResult> {

        private static final long MIN_TASK_TIME_MS = 3000;

        private Bitmap scaledBitmap;
        private List<Classifier.Recognition> results;

        private String errorMessage = null;
        private long taskTime;

        protected ClassificationTaskResult doInBackground(byte[]... pictures) {
            final long startTime = System.currentTimeMillis();
            final int count = pictures.length;
            Timber.d("ClassifyImageTask with " + count + " byte array params (should be 1)");
            final byte[] picture = pictures[0];
            try {
                Bitmap bitmap = BitmapFactory.decodeByteArray(picture, 0, picture.length);
                scaledBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, false);
                results = classifier.recognizeImage(scaledBitmap);
                Timber.d("classification results: " + results.toString());
            } catch (Exception e) {
                Timber.e("error in classification task: " + e.toString());
                errorMessage = e.toString();
                return ClassificationTaskResult.FAIL;
            }

            taskTime = System.currentTimeMillis() - startTime;
            if (taskTime < MIN_TASK_TIME_MS) {
                try {
                    Thread.sleep(MIN_TASK_TIME_MS - taskTime);
                } catch (InterruptedException e) {
                    Timber.e("finishing task, interrupted during min task time sleep: " + e);
                }
            }

            return ClassificationTaskResult.SUCCESS;
        }

        private void showResultToast(final String message, final int color, final int resultIcon) {
            SuperActivityToast.create(MainActivity.this, new Style(), Style.TYPE_BUTTON)
                    .setText(message)
                    .setDuration(Style.DURATION_VERY_LONG)
//                    .setHeight(300)
//                    .setTextSize(30)
                    .setTextColor(getColor(R.color.white))
                    .setIconResource(resultIcon)
                    .setGravity(Gravity.TOP)
                    .setColor(getColor(color))
                    .setAnimations(Style.ANIMATIONS_FLY).show();
        }

        protected void onPostExecute(final ClassificationTaskResult exitCode) {
            switch (exitCode) {
                case SUCCESS:
                    // If the foundResult is sufficiently confident, show success screen.
                    final Recognition foundResult  = renderClassificationResultDisplay(results);
                    if (foundResult != null) {
                        shareButton.setBackgroundColor(getColor(R.color.md_green_500));
                        shareButton.setText(getString(R.string.share_success));
                        showResultToast(getString(R.string.target_item) + "!", R.color.md_green_500, R.drawable.check_mark_75);
                    } else {
                        shareButton.setBackgroundColor(getColor(R.color.md_red_500));
                        shareButton.setText(getString(R.string.share_failure));
                        showResultToast("Not a " + getString(R.string.target_item) + "!", R.color.md_red_500, R.drawable.x_mark_75);
                    }
                    resultLayout.setVisibility(View.VISIBLE);
                    imageViewResult.setImageBitmap(scaledBitmap);
                    shareButton.setOnClickListener(new OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Image description used for the social share message.
                            final String imageDescription;
                            if (foundResult != null) {
                                imageDescription = "Successful find!";
                            } else {
                                imageDescription = "I failed";
                            }
                            Timber.d("hit share button, share message set to: " + imageDescription);
                            shareClassifierResult(imageDescription);
                        }
                    });
                    // Save the current state of the screen.
                    lastScreenShot = takeShareableScreenshot();
                    break;
                case FAIL: // Software error - Unable to classify image.
                    resultLayout.setVisibility(View.GONE);
                    makeToast(errorMessage);
                    break;
            }
            hideLoadingDialog();
        }
    }

    // ** Social Sharing Intent ** //

    private Bitmap takeShareableScreenshot() {
        // https://stackoverflow.com/questions/2661536/how-to-programmatically-take-a-screenshot-in-android
//        cameraActionLayout.setVisibility(View.GONE);
//        resultLayout.setVisibility(View.GONE);
        try {
            // create bitmap screen capture
            View v1 = getWindow().getDecorView().getRootView();
            v1.setDrawingCacheEnabled(true);
            return Bitmap.createBitmap(v1.getDrawingCache());
        } catch (Throwable e) {
            // Several error may come out with file handling or OOM
            e.printStackTrace();
            makeToast(e.toString());
            Timber.e("Error capturing screenshot: " + e.toString());
            return null;
        }
//        finally {
//            cameraActionLayout.setVisibility(View.VISIBLE);
//            resultLayout.setVisibility(View.VISIBLE);
//        }
    }

    private void shareClassifierResult(final String imageDescription) {
        if (lastScreenShot == null) {
            makeToast(getString(R.string.taking_new_screenshot));
            lastScreenShot = takeShareableScreenshot();
        }

        String path = MediaStore.Images.Media.insertImage(getContentResolver(), lastScreenShot, imageDescription, null);
        Uri uri = Uri.parse(path);

        Intent tweetIntent = new Intent(Intent.ACTION_SEND);
        tweetIntent.setType("image/jpeg");
        tweetIntent.putExtra(Intent.EXTRA_STREAM, uri);
        startActivity(Intent.createChooser(tweetIntent, getString(R.string.share_result_prompt)));
    }

}
