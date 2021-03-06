package com.wizzardo.http.mapping;

import com.wizzardo.http.Named;

import java.util.regex.Pattern;

/**
* Created by wizzardo on 27.03.15.
*/
class UrlMappingMatcherPattern<T extends Named> extends UrlMappingMatcher<T> {
    Pattern pattern;

    protected UrlMappingMatcherPattern(UrlMapping<T> parent, String pattern) {
        super(parent);
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    protected boolean matches(String urlPart) {
        return pattern.matcher(urlPart).matches();
    }

    @Override
    protected boolean checkNextPart() {
        return false;
    }
}
