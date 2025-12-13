package com.example.csmarketoverlay;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiRequestExecutor {
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static ExecutorService getInstance() {
        return executor;
    }
}