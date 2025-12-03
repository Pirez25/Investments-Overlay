package com.example.csmarketoverlay;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(); //declarei handler aqui para nao ter que tar sempre a declarar

    private final String[] item_id = { //biblioteca de itens a ser buscado na API(hash_name)
            //aqui tenho de ir buscar o nome da hashname á base de dados
            "Gallery Case",
            "CS20 Case",
            "Dreams & Nightmares Case",
            "M4A1-S | Vaporwave (Field-Tested)",
            "Number K | The Professionals"
    };

    private final String API_URL = "https://steamcommunity.com/market/priceoverview/?currency=3&appid=730&market_hash_name=";//variavel com API

    private TextView invPriceTextView;// Variavel para exibir o preço total do inventário

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Define o layout da Activity

        //buscar variaveis ao layout(XML)
        invPriceTextView = findViewById(R.id.invprice);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);


        btnStart.setOnClickListener(v -> startOverlayWithPermission());//Chama a funçao do overlay
        btnStop.setOnClickListener(v -> stopService(new Intent(this, OverlayService.class)));//para a classe do overlay


        new Handler().postDelayed(this::startOverlayWithPermission, 200);// Assim que abrir o app, inicia automaticamente o overlay
    }

    private void startOverlayWithPermission() //funçao para iniciar a classe do overlay
    {
        // Verifica permissão de overlay (necessária em Android 6+)
        //Se o Android for 6 ou superior e a app não tiver permissão, o código dentro do if será executado.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this))
        {
//Build.VERSION.SDK_INT: retorna a versão do Android. Build.VERSION_CODES.M: constante que representa o Android 6 (Marshmallow). Settings.canDrawOverlays(this): verifica se a app já tem permissão para sobrepor
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,//Abrir definicoes permissoes de Overlay
                    Uri.parse("package:" + getPackageName()));//vai dizer que é esta aplicacao que ele quer referenciar quando tiver a dar a permissao nas definicoes
            startActivity(intent);
            return;//return para sair do metodo porque o utilizador ainda nao deu permissao
        }

        // Garante que o serviço overlay é reiniciado limpo
        stopService(new Intent(this, OverlayService.class));//se já tiver ativo um overlay ele fecha
        handler.postDelayed(() -> startService(new Intent(this, OverlayService.class)), 0);

        // Atualiza preços logo depois
        handler.postDelayed(this::fetchAndSendPrices, 200);
    }

    private void fetchAndSendPrices() {
        new Thread(() -> {// criar nova thread para evitar crash ao buscar preços
            double total = 0.0;// variável para somar o total dos preços

            for (int i = 0; i < item_id.length; i++)
            {
                String item = item_id[i];
                double preco = fetchPrice(item);//chama funçao fetchPrice com o item

                //depois de executar o fetch price executa em baixo
                double precoMultiplicado = applyMultiplier(i, preco);
                total += precoMultiplicado;
                double finalTotal = total;

                // Atualiza o preço total do inventario no main imediatamente
                handler.post(() -> invPriceTextView.setText(
                        String.format("Total Inventory: %.2f€", finalTotal)
                ));

                // Atualiza overlay com preços sem multiplicar
                Intent it = new Intent("UPDATE_OVERLAY_ITEM");
                it.putExtra("item_name", item);
                it.putExtra("item_price", preco);
                sendBroadcast(it);
            }

            double finalTotal = total;
            handler.post(() -> invPriceTextView.setText(
                    String.format("Total Inventory: %.2f€", finalTotal)
            ));

        }).start();

        // Atualização automática a cada 2 min
        handler.postDelayed(this::fetchAndSendPrices, 120_000);
    }
    private static final String TAG = "MainActivity";//Tag para log quando encontra um try,catch(Exeption)
    private double fetchPrice(String item)//funçao fetch price que recebe o item
    {
        try {

            String urlStr = API_URL + URLEncoder.encode(item, "UTF-8");// Mete o nome do item na hash_name da API
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();//abre ligaçao http
            c.setRequestProperty("User-Agent", "Mozilla/5.0");// Evita bloqueios da Steam API
            c.setConnectTimeout(5000);// Tempo máximo de conexão
            c.setReadTimeout(5000);// Tempo máximo de leitura

            BufferedReader reader = new BufferedReader(new InputStreamReader(c.getInputStream()));//lê a resposta da API
            StringBuilder response = new StringBuilder();//cria objeto para acumular o texto
            String line;
            while ((line = reader.readLine()) != null)
                response.append(line);
            reader.close();

            JSONObject json = new JSONObject(response.toString());
            /*{ Exemplo do json file
             "success": true,
             "lowest_price": "3,47€",
             "median_price": "3,50€"
            }*/
            if (json.has("lowest_price")) {
                return parsePrice(json.getString("lowest_price"));
            } else if (json.has("median_price")) {
                return parsePrice(json.getString("median_price"));
            }

        } catch (Exception e) {
            Log.e(TAG, "Erro ao buscar preço de " + item, e);
        }
        return 0.0;//retorna 0 para nao dar erro caso nao consiga receber o valor
    }

    private double parsePrice(String valorBruto) {
        try {//Remove símbolos e letras
            return Double.parseDouble(valorBruto.replaceAll("[^\\d,\\.]", "").replace(",", "."));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double applyMultiplier(int index, double price) {
        switch (index) {//aqui tenho que ir buscar a quantidade á database
            case 0: return price * 203;
            case 1: return price * 254;
            case 2: return price * 102;
            default: return price;
        }
    }
}
