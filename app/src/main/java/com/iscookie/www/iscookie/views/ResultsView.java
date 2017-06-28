package com.iscookie.www.iscookie.views;


import com.iscookie.www.iscookie.Classifier.Recognition;

import java.util.List;

public interface ResultsView {
    public void setResults(final List<Recognition> results);
}