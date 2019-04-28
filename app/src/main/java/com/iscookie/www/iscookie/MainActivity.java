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
import com.camerakit.CameraKit;
import com.camerakit.CameraKitView;
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
import com.iscookie.www.iscookie.utils.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import mehdi.sakout.fancybuttons.FancyButton;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity implements ConfettiActivity {

    private static final Logger LOGGER = new Logger();

    // for custom model refer to: https://github.com/tensorflow/tensorflow/issues/2883

    // Values for google's inception model (from the original paper findings).
    private static final int INPUT_SIZE = 224;

    // Default parameters.
    private Classifier.Model model = Classifier.Model.QUANTIZED;
    private Classifier.Device device = Classifier.Device.CPU;
    private int numThreads = 1;

    private static final float CONFIDENCE_THRESHOLD = .3f;

    private static Classifier classifier; // Tensorflow classifier.
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
    private SpinKitView loadingSpinner;
    private ImageView imageViewResult;
    private CameraKitView cameraKitView;


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
        cameraKitView = (CameraKitView) findViewById(R.id.camera);
        // cameraKitView.refreshDrawableState();

        topVolumeView = (ImageView) findViewById(R.id.topVolumeView);
//        topVolumeView.setImageDrawable(); // set volume mute/unmute image based on initial mutesetting.
        topVolumeView.setOnClickListener(v -> {
            Timber.d("muted: " + isMuted);
            isMuted = !isMuted;
            // TODO: change the mute logo to muted or unmuted.
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
        loadingSpinner = (SpinKitView) findViewById(R.id.loadingSpinner);

        btnDetectObject.setVisibility(View.GONE);

//        cameraKitView.setCameraListener(new CameraKitView.CameraListener() {
//            @Override
//            public void onPictureTaken(byte[] picture) {
//                showLoadingDialog();
//                Timber.d("starting ClassifyImageTask with picture length " + picture.length);
//                new ClassifyImageTask().execute(picture);
//            }
//        });

        btnToggleCamera.setOnClickListener(v -> cameraKitView.toggleFacing());
        // set default facing direction.
        cameraKitView.setFacing(CameraKit.FACING_BACK);

        btnDetectObject.setOnClickListener(v -> {
            try {
                cameraKitView.captureImage((cameraKitView, picture) -> {
                    showLoadingDialog();
                    Timber.d("starting ClassifyImageTask with picture length " + picture.length);
                    new ClassifyImageTask().execute(picture);
                });
            } catch (Exception e) {
                makeToast(getString(R.string.camera_error));
                // Attempt to restart the camera after a failed image retrieval.
                cameraKitView.onStop();
                cameraKitView.onStart();
            }
        });

        if (classifier == null) {
            initTensorFlowAndLoadModel();
        } else {
            btnDetectObject.setVisibility(View.VISIBLE);
        }
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
    protected void onStart() {
        super.onStart();
        cameraKitView.onStart();
    }

    @Override
    protected void onStop() {
        cameraKitView.onStop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraKitView.onResume();
    }

    @Override
    protected void onPause() {
        cameraKitView.onStop();
        hideLoadingDialog();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideLoadingDialog();
        executor.execute(() -> {
            if (classifier != null) {
                classifier.close();
            }
        });
    }

    private void initTensorFlowAndLoadModel() {
        loadingSpinner.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            if (classifier != null) {
                LOGGER.d("Closing classifier.");
                classifier.close();
                classifier = null;
            }

            // Default to float model if quantized is not supported.
            if (device == Classifier.Device.GPU && model == Classifier.Model.QUANTIZED) {
                LOGGER.d("Creating float model: GPU doesn't support quantized models.");
                model = Classifier.Model.FLOAT;
            }

            try {
                LOGGER.d("Creating classifier (model=%s, device=%s, numThreads=%d)", model, device, numThreads);
                classifier = Classifier.create(MainActivity.this, model, device, numThreads);
                doneLoadingClassifier();
            } catch (IOException e) {
                LOGGER.e(e, "Failed to create classifier.");
            }
        });
    }

    private void makeToast(final String msg) {
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }

    private void doneLoadingClassifier() {
        runOnUiThread(() -> {
            Timber.d("done loading classifier");
            loadingSpinner.setVisibility(View.GONE);
            btnDetectObject.setVisibility(View.VISIBLE);
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
                if (classifier == null) { // last minute init.
                    initTensorFlowAndLoadModel();
                }
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
                    .setTextColor(getResources().getColor(R.color.white))
                    .setIconResource(resultIcon)
                    .setGravity(Gravity.TOP)
                    .setColor(getResources().getColor(color))
                    .setAnimations(Style.ANIMATIONS_FLY).show();
        }

        protected void onPostExecute(final ClassificationTaskResult exitCode) {
            switch (exitCode) {
                case SUCCESS:
                    // If the foundResult is sufficiently confident, show success screen.
                    final Recognition foundResult  = renderClassificationResultDisplay(results);
                    if (foundResult != null) {
                        shareButton.setBackgroundColor(getResources().getColor(R.color.md_green_500));
                        shareButton.setText(getString(R.string.share_success));
                        showResultToast(getString(R.string.target_item) + "!", R.color.md_green_500, R.drawable.check_mark_75);
                    } else {
                        shareButton.setBackgroundColor(getResources().getColor(R.color.md_red_500));
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

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        cameraKitView.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}
