import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class IdealistaMonitor {

    private static final String SEARCH_URL = "https://www.idealista.com/areas/venta-viviendas/con-precio-hasta_850000,precio-desde_500000,pisos,de-dos-dormitorios,de-tres-dormitorios,de-cuatro-cinco-habitaciones-o-mas,solo-bajos/?shape=%28%28%28_%7B%7BuFlmtV%7Dq%40%3FqFYqKiBuZmKuDs%40aIyDkOgGaIgGgEsEuIkPaD%7DHkCwIgLwa%40oFcVyBiGuDoO%7DJiZ_DgL%7BQed%40%7B%40sESmF%3Fw%5Cz%40_Wf%40%7DH%3Fkh%40nAwWpKhh%40oFiZiE_RwBaHg%40sJ%3Fax%40R_Rz%40%7BRz%40wIxGw%5C%7CEqOnFoO%7EO%7D%60%40lHqOxGoOlHqOp%5Cyj%40%60IaMxGwIrPkPdGsEtN%7DHxGmA%60f%40%3FzEXzLhBxLbCfLxD%7CJrEtD%7CCfEhBtDvDtIfL%60DlFxGzMdGpOzVtk%40lCzH%60IzRtIzRvIdQ%7ECrEdGdLfL%60R%7CJtNjO%7EV%7CJ%7EQ%60DzMvB%60MpAfLz%40zRf%40pEnAr%5D%60Dng%40z%40lK%3FvlASdLcBxWaDnTwBbHgLdQaIfG_PfQmClAoFbHyi%40va%40wZrXoFvDmTdQyXjPqKlFoMnF_P%7CC%29%29%29"; // ← reemplazar
    private static final String TELEGRAM_TOKEN = System.getenv("TELEGRAM_TOKEN");
    private static final String TELEGRAM_CHAT_ID = System.getenv("TELEGRAM_CHAT_ID");

    private static final Set<String> seenAds = AdPersistence.loadSeenAds();
    private static final OkHttpClient unsafeClient = getUnsafeHttpClient();

    public static void main(String[] args) throws Exception {
        System.out.println("🟢 Iniciando monitor de Idealista...");
        LocalDate lastKeepAliveDate = null;
        while (true) {

            // Obtener fecha y hora actual
            LocalDateTime now = LocalDateTime.now();

            // 🕙 Enviar keep-alive diario a las 22:00
            if (now.getHour() == 22 && (lastKeepAliveDate == null || !now.toLocalDate().equals(lastKeepAliveDate))) {
                sendTelegramMessage("✅ Idealista Monitor está corriendo (ping diario 🕙)");
                lastKeepAliveDate = now.toLocalDate();
            }

            checkForNewAds();
            Thread.sleep(60 * 1000); // Esperar 1 minuto
        }
    }

    private static void checkForNewAds() {
        try {
            Document doc = getDocumentWithOkHttp(SEARCH_URL);

            Elements adElements = doc.select("a.item-link"); // Cambiar si el selector no coincide
            for (Element ad : adElements) {
                String url = ad.attribute("href").getValue();
                if (!seenAds.contains(url)) {
                    System.out.println("🔔 Nuevo anuncio: " + url);
                    seenAds.add(url);
                    sendTelegramMessage("🏠 Nuevo anuncio:\nhttps://www.idealista.com/"+url);
                    AdPersistence.appendSeenAd(url);
                }
            }

        } catch (IOException e) {
            System.err.println("❌ Error al obtener la página: " + e.getMessage());
        }
    }

    private static void sendTelegramMessage(String message) {
        String url = "https://api.telegram.org/bot" + TELEGRAM_TOKEN + "/sendMessage";

        RequestBody body = new FormBody.Builder()
                .add("chat_id", TELEGRAM_CHAT_ID)
                .add("text", message)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();

        try (Response response = unsafeClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("❌ Error enviando a Telegram: " + response);
            }
        } catch (IOException e) {
            System.err.println("❌ Error en conexión Telegram: " + e.getMessage());
        }
    }

    private static Document getDocumentWithOkHttp(String url) throws IOException {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Connection", "keep-alive")
                .header("Referer", "https://www.idealista.com/")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP error fetching URL. Status=" + response.code());
            }
            return Jsoup.parse(response.body().string());
        }
    }

    private static Document getDocumentWithOkHttpBypassingSSL(String url) throws IOException {
        try {
            // TrustManager que acepta cualquier certificado
            javax.net.ssl.X509TrustManager trustManager = new javax.net.ssl.X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0]; // ← importante: NO retornar null
                }

                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }
            };

            // Crear contexto SSL
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{trustManager}, new java.security.SecureRandom());

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();

            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                    .header("Connection", "keep-alive")
                    .header("Referer", "https://www.idealista.com/")
                    .build();

            try (okhttp3.Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP error fetching URL. Status=" + response.code() + ", URL=[" + url + "]");
                }
                String html = response.body().string();
                return Jsoup.parse(html);
            }

        } catch (Exception e) {
            throw new IOException("Error al obtener la página (SSL + OkHttp)", e);
        }
    }
    private static OkHttpClient getUnsafeHttpClient() {
        try {
            javax.net.ssl.TrustManager[] trustAllCerts = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return new java.security.cert.X509Certificate[0];
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
            };

            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            return new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAllCerts[0])
                    .hostnameVerifier((hostname, session) -> true)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Error creando OkHttpClient inseguro", e);
        }
    }
}