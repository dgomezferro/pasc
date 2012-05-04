package com.yahoo.pasc;

class CrashFailureHandler implements FailureHandler {
    @Override
    public void handleFailure(Exception e) {
        e.printStackTrace();
        System.exit(-1);
    }
}
