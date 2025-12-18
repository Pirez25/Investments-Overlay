package com.example.csmarketoverlay;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Esta classe gere a execução de pedidos à API, garantindo que são feitos um de cada vez.
public class ApiRequestExecutor {
    // Cria um executor que usa uma única thread. Isto garante que os pedidos são executados em série.
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Devolve a instância do executor.
    public static ExecutorService getInstance() {
        return executor;
    }
}
