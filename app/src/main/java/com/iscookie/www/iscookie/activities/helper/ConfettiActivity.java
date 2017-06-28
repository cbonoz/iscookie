package com.iscookie.www.iscookie.activities.helper;


import com.github.jinatonic.confetti.ConfettiManager;

public interface ConfettiActivity {

    ConfettiManager generateOnce();
    ConfettiManager generateStream();
    ConfettiManager generateInfinite();

}
