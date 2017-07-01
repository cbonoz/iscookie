package com.iscookie.www.iscookie.activities;

import android.content.Intent;
import android.util.Log;

import com.daimajia.androidanimations.library.Techniques;
import com.iscookie.www.iscookie.BuildConfig;
import com.iscookie.www.iscookie.MainActivity;
import com.iscookie.www.iscookie.R;
import com.viksaa.sssplash.lib.activity.AwesomeSplash;
import com.viksaa.sssplash.lib.cnst.Flags;
import com.viksaa.sssplash.lib.model.ConfigSplash;

import timber.log.Timber;
import timber.log.Timber.DebugTree;

public class SplashActivity extends AwesomeSplash {

    //DO NOT OVERRIDE onCreate()!
    //if you need to start some services do it in initSplash()!

    private void setUpTimber() {

        if (BuildConfig.DEBUG) {
            Timber.plant(new DebugTree());
        }
//        else {
            // No logging in non-debug builds.
            // Timber.plant(new CrashReportingTree());
//        }
        Timber.d("finishing setting up timber in " + Timber.asTree().getClass() + " mode");
    }

    @Override
    public void initSplash(ConfigSplash configSplash) {
        setUpTimber();

        final int duration;
        if (BuildConfig.DEBUG) {
            duration = 500;
        } else {
            // Production duration for into splash screen animations.
            duration = 1000;
        }
        Timber.d("Splash duration: " + duration);

        //Customize Circular Reveal
        configSplash.setBackgroundColor(R.color.white); //any color you want form colors.xml
        configSplash.setAnimCircularRevealDuration(duration); //int ms
        configSplash.setRevealFlagX(Flags.REVEAL_RIGHT);  //or Flags.REVEAL_LEFT
        configSplash.setRevealFlagY(Flags.REVEAL_BOTTOM); //or Flags.REVEAL_TOP

        //Customize Logo
        configSplash.setLogoSplash(R.drawable.cookie_logo_175); //or any other drawable
        configSplash.setAnimLogoSplashDuration(duration); //int ms
        configSplash.setAnimLogoSplashTechnique(
                Techniques.Bounce); //choose one form Techniques (ref: https://github.com/daimajia/AndroidViewAnimations)

        //Customize Title
        configSplash.setTitleSplash(getString(R.string.app_name));
        configSplash.setTitleTextColor(R.color.md_deep_orange_A200);
        configSplash.setTitleTextSize(30f); //float value
        configSplash.setAnimTitleDuration(duration);
        configSplash.setAnimTitleTechnique(Techniques.FlipInX);

    }

    @Override
    public void animationsFinished() {
        // tart main activity after animation finished.
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // use to wrap up the current activity so the user can't back up to it.

    }
}
