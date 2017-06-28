package com.iscookie.www.iscookie.activities;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.daimajia.androidanimations.library.Techniques;
import com.iscookie.www.iscookie.BuildConfig;
import com.iscookie.www.iscookie.R;
import com.viksaa.sssplash.lib.activity.AwesomeSplash;
import com.viksaa.sssplash.lib.cnst.Flags;
import com.viksaa.sssplash.lib.model.ConfigSplash;

import timber.log.Timber;

public class SplashActivity extends AwesomeSplash {

    //DO NOT OVERRIDE onCreate()!
    //if you need to start some services do it in initSplash()!


    @Override
    public void initSplash(ConfigSplash configSplash) {

        final int duration;
        if (BuildConfig.DEBUG) {
            duration = 500;
        } else {
            // Production duration for into splash screen animations.
            duration = 1000;
        }
        Timber.d("Splash duration: " + duration);

        //Customize Circular Reveal
        configSplash.setBackgroundColor(R.color.primary); //any color you want form colors.xml
        configSplash.setAnimCircularRevealDuration(duration); //int ms
        configSplash.setRevealFlagX(Flags.REVEAL_RIGHT);  //or Flags.REVEAL_LEFT
        configSplash.setRevealFlagY(Flags.REVEAL_BOTTOM); //or Flags.REVEAL_TOP

        //Choose LOGO OR PATH; if you don't provide String value for path it's logo by default

        //Customize Logo
        configSplash.setLogoSplash(R.mipmap.ic_launcher); //or any other drawable
        configSplash.setAnimLogoSplashDuration(duration); //int ms
        configSplash.setAnimLogoSplashTechnique(
                Techniques.Bounce); //choose one form Techniques (ref: https://github.com/daimajia/AndroidViewAnimations)

        //Customize Title
        configSplash.setTitleSplash(getString(R.string.app_name));
        configSplash.setTitleTextColor(R.color.black);
        configSplash.setTitleTextSize(30f); //float value
        configSplash.setAnimTitleDuration(duration);
        configSplash.setAnimTitleTechnique(Techniques.FlipInX);
        configSplash.setTitleFont("fonts/myfont.ttf"); //provide string to your font located in assets/fonts/

    }

    @Override
    public void animationsFinished() {
        // tart main activity after animation finished.
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish(); // use to wrap up the current activity so the user can't back up to it.

    }
}
