package com.example.csmarketoverlay;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ApiRequestExecutor {

    // Cria um executor que reutiliza threads e cria novas conforme necessário.
    // Isto permite que vários pedidos à API sejam executados em paralelo.
    private static final ExecutorService executor = Executors.newCachedThreadPool();

    public static ExecutorService getInstance() {
        return executor;
    }
}
