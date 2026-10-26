package app.maxund;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.content.Intent;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.qrcode.QRCodeWriter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import org.json.*;
import java.util.UUID;

/** Foreground-only text messenger. Account state lives only in RAM. */
public final class MainActivity extends Activity {
    private LinearLayout root, content;
    private TextView status;
    private MaxConnection connection;
    private final String deviceId = UUID.randomUUID().toString();
    private String loginToken, smsToken, passwordTrack;
    private long myUserId = Long.MIN_VALUE;
    private JSONArray chats = new JSONArray();
    private Long chatId;
    private String chatTitle;
    private boolean foreground, busy;
    private int generation;
    private String qrTrack;
    private long qrDeadline;
    private long nextCodeRequest;
    private int qrInterval;
    private final Runnable qrPoll = this::pollQr;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable keepalive = new Runnable() {
        public void run() {
            if (connection != null && loginToken != null && foreground) {
                try { connection.call(1, Protocol.object("interactive", false), p -> {}); }
                catch (JSONException ignored) {}
                handler.postDelayed(this, 30000);
            }
        }
    };
    private interface Action { void run() throws Exception; }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().setStatusBarColor(Color.rgb(23, 35, 54));
        getWindow().setNavigationBarColor(Color.rgb(23, 35, 54));
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24, 24, 24, 16);
        root.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        root.setBackgroundColor(Color.rgb(244, 247, 251));
        TextView title = text("MAXund", 28); title.setTextColor(Color.rgb(27, 82, 145)); root.addView(title);
        root.addView(text("Неофициальный • экспериментальный клиент", 12));
        status = text("", 14); root.addView(status);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(24 + insets.getSystemWindowInsetLeft(), 24 + insets.getSystemWindowInsetTop(),
                24 + insets.getSystemWindowInsetRight(), 16 + insets.getSystemWindowInsetBottom());
            return insets;
        });
        loginScreen();
    }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView text(String value, int size) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setPadding(0, 10, 0, 10);
        v.setTextColor(Color.rgb(23, 35, 54)); return v;
    }
    private EditText field(String hint, int type) {
        EditText v = new EditText(this); v.setHint(hint); v.setInputType(type); v.setSaveEnabled(false); content.addView(v); return v;
    }
    private void button(LinearLayout parent, String label, Action action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setTextSize(15); b.setMinHeight(dp(48));
        b.setBackground(shape(Color.rgb(48, 145, 211), 12));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, -2); bp.setMargins(0, dp(5), dp(4), dp(5));
        // Toolbar controls share the row instead of each occupying its full width.
        if (parent.getOrientation() == LinearLayout.HORIZONTAL) bp = new LinearLayout.LayoutParams(0, -2, 1);
        parent.addView(b, bp);
        b.setOnClickListener(v -> {
            try { action.run(); } catch (Exception e) { report("Не удалось выполнить действие."); }
        });
    }
    private void report(String message) { runOnUiThread(() -> { busy = false; status.setText(message); }); }
    private void loginScreen() {
        chatId = null; content.removeAllViews();
        content.addView(text("Вход по номеру телефона", 22));
        content.addView(text("Без загрузки контактов, аналитики и сохранения переписки на диск.\nMAX всё равно получает номер, сообщения и сетевые метаданные. Сквозное шифрование не добавлено.", 14));
        EditText phone = field("+79991234567", InputType.TYPE_CLASS_PHONE);
        button(content, "Получить код", () -> {
            if (busy) return;
            String number = phone.getText().toString().trim();
            if (!number.matches("\\+[1-9][0-9]{7,14}")) { report("Введите номер в международном формате: +79991234567"); return; }
            long now = SystemClock.elapsedRealtime();
            if (now < nextCodeRequest) { report("Подождите перед повторным запросом кода. При серверном ограничении используйте QR-вход."); return; }
            nextCodeRequest = now + 60000;
            busy = true; status.setText("Подключение…");
            connect(() -> connection.call(17, json("phone", number, "type", "START_AUTH", "language", "ru"), p -> ui(() -> {
                smsToken = p.optString("token", "");
                if (smsToken.isEmpty()) { report("Сервер не выдал токен входа. Эта версия протокола может не поддерживаться."); return; }
                phone.setText(""); busy = false; codeScreen();
            })));
        });
        button(content, "Войти по QR через официальный MAX", () -> {
            if (busy) return; busy = true; status.setText("Запрос QR для входа…");
            connect(() -> connection.call(288, new JSONObject(), p -> ui(() -> showQr(p))));
        });
        button(content, "Подтверждение возраста — официальный MAX", this::digitalId);
        button(content, "О приватности и ограничениях", this::privacy);
    }
    private void showQr(JSONObject p) {
        busy = false;
        String link = p.optString("qrLink", ""); String track = p.optString("trackId", "");
        if (!link.startsWith("https://") || link.length() > 4096 || track.isEmpty()) {
            report("Сервер не выдал поддерживаемый QR входа."); return;
        }
        try {
            var matrix = new QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888);
            for (int y = 0; y < 512; y++) for (int x = 0; x < 512; x++) bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
            content.removeAllViews(); status.setText("QR только для входа — не для подтверждения возраста");
            content.addView(text("Отсканируйте и подтвердите вход в официальном MAX на другом устройстве. Не передавайте QR посторонним.", 16));
            ImageView image = new ImageView(this); image.setImageBitmap(bitmap); image.setContentDescription("QR входа в MAXund");
            content.addView(image, new LinearLayout.LayoutParams(-1, 0, 1));
            button(content, "Отмена", this::logout);
            qrTrack = track; qrInterval = Math.min(10000, Math.max(2000, p.optInt("pollingInterval", 3000)));
            long expires = p.optLong("expiresAt", System.currentTimeMillis() + 120000);
            qrDeadline = Math.min(expires, System.currentTimeMillis() + 120000);
            handler.removeCallbacks(qrPoll); handler.postDelayed(qrPoll, qrInterval);
        } catch (Exception e) { report("Не удалось отобразить QR входа."); }
    }
    private void pollQr() {
        if (!foreground || qrTrack == null || connection == null) return;
        if (System.currentTimeMillis() >= qrDeadline) {
            qrTrack = null; content.removeAllViews(); report("QR истёк. Запросите новый QR входа.");
            button(content, "На экран входа", this::logout); return;
        }
        connection.call(289, json("trackId", qrTrack), p -> ui(() -> {
            JSONObject state = p.optJSONObject("status");
            if (state != null && state.optBoolean("loginAvailable")) {
                String track = qrTrack; qrTrack = null;
                connection.call(291, json("trackId", track), result -> ui(() -> handleAuth(result, false)));
            } else handler.postDelayed(qrPoll, qrInterval);
        }));
    }
    private void digitalId() {
        new AlertDialog.Builder(this).setTitle("Подтверждение возраста")
            .setMessage("MAXund не имеет интеграции с Цифровым ID и не выдаёт QR для кассы. Если подтверждение уже настроено, откройте Цифровой ID в официальном MAX. Будет запущено отдельное приложение.")
            .setPositiveButton("Открыть MAX", (dialog, which) -> {
                Intent intent = getPackageManager().getLaunchIntentForPackage("ru.oneme.app");
                if (intent == null) report("Официальный MAX не установлен или недоступен.");
                else startActivity(intent);
            }).setNegativeButton("Отмена", null).show();
    }
    private void codeScreen() {
        content.removeAllViews(); status.setText("Введите полученный код. Не отправляйте его разработчикам.");
        EditText code = field("Код", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        button(content, "Войти", () -> {
            if (busy) return;
            String value = code.getText().toString().trim();
            if (!value.matches("[0-9]{4,10}")) { report("Проверьте код."); return; }
            busy = true;
            connection.call(18, json("token", smsToken, "verifyCode", value, "authTokenType", "CHECK_CODE"), p -> ui(() -> {
                code.setText(""); handleAuth(p, false);
            }));
        });
        button(content, "Отмена", this::logout);
    }
    private void handleAuth(JSONObject payload, boolean afterPassword) {
        busy = false;
        String token = Protocol.loginToken(payload, afterPassword);
        if (!token.isEmpty()) {
            loginToken = token; smsToken = null; passwordTrack = null;
            connection.call(19, ProtocolLogin(), p -> ui(() -> signedIn(p)));
            return;
        }
        String track = Protocol.passwordTrack(payload);
        if (!track.isEmpty()) {
            passwordTrack = track; smsToken = null; passwordScreen(); return;
        }
        report("Сервер не выдал сессию или поддерживаемый запрос 2FA. Требуется проверка актуального протокола.");
    }
    private void passwordScreen() {
        content.removeAllViews(); busy = false;
        status.setText("Двухэтапная аутентификация");
        content.addView(text("Введите пароль MAX", 24));
        content.addView(text("Пароль отправляется только серверу MAX по защищённому соединению и не сохраняется. Не передавайте его разработчикам.", 14));
        EditText password = field("Пароль 2FA", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        button(content, "Подтвердить пароль", () -> {
            if (busy) return;
            String value = password.getText().toString();
            if (value.isEmpty()) { report("Введите пароль 2FA."); return; }
            busy = true; password.setText("");
            connection.call(115, Protocol.password(passwordTrack, value), p -> ui(() -> handleAuth(p, true)));
        });
        button(content, "Отмена", this::logout);
    }
    private JSONObject ProtocolLogin() { try { return Protocol.login(loginToken); } catch (JSONException e) { throw new IllegalStateException(e); } }
    private JSONObject json(Object... values) { try { return Protocol.object(values); } catch (JSONException e) { throw new IllegalStateException(e); } }
    private void ui(Runnable r) { final int current = generation; runOnUiThread(() -> { if (foreground && current == generation) r.run(); }); }
    private void connect(Runnable ready) {
        if (connection != null) connection.close();
        final int current = ++generation;
        connection = new MaxConnection(new MaxConnection.Events() {
            public void ready() { ui(() -> {
                if (current != generation || !foreground) return;
                try { connection.call(6, Protocol.hello(deviceId), p -> ui(ready)); }
                catch (JSONException e) { report("Ошибка инициализации."); }
            }); }
            public void packet(JSONObject packet) { ui(() -> {
                if (current != generation || !foreground) return;
                if (packet.optInt("opcode") == 128 && chatId != null) {
                    JSONObject p = packet.optJSONObject("payload");
                    if (p != null && p.optLong("chatId", Long.MIN_VALUE) == chatId) loadHistory();
                }
            }); }
            public void error(String message) { if (current == generation) report(message); }
        });
        connection.connect();
    }
    private void signedIn(JSONObject result) {
        busy = false; status.setText("Соединение с MAX установлено. Сессия хранится только в памяти.");
        JSONObject profile = result.optJSONObject("profile");
        JSONObject contact = profile == null ? null : profile.optJSONObject("contact");
        if (contact != null) myUserId = contact.optLong("id", Long.MIN_VALUE);
        JSONArray list = result.optJSONArray("chats"); if (list != null) chats = list;
        handler.removeCallbacks(keepalive); handler.postDelayed(keepalive, 30000);
        chatsScreen();
    }
    private void chatsScreen() {
        chatId = null; content.removeAllViews();
        content.addView(text("Чаты", 24));
        button(content, "Обновить чаты", () -> {
            if (busy) return; busy = true;
            // Re-establish the session instead of synchronizing device contacts.
            connect(() -> connection.call(19, ProtocolLogin(), p -> ui(() -> signedIn(p))));
        });
        ScrollView scroll = new ScrollView(this); LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        if (chats.length() == 0) rows.addView(text("Список чатов пуст или формат ответа не поддерживается. Можно открыть чат по известному ID.", 15));
        for (int i = 0; i < chats.length(); i++) {
            JSONObject chat = chats.optJSONObject(i); if (chat == null || !chat.has("id")) continue;
            long id = chat.optLong("id"); String name = chat.optString("title", "Чат " + id);
            if (name.trim().isEmpty()) name = "Чат " + id;
            final String label = name;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(8), dp(12), dp(8), dp(12)); row.setBackgroundColor(Color.WHITE);
            TextView avatar = text(label.substring(0, 1).toUpperCase(java.util.Locale.ROOT), 22);
            avatar.setGravity(Gravity.CENTER); avatar.setTextColor(Color.WHITE);
            avatar.setBackground(shape(Color.rgb(74, 157, 206), 30));
            row.addView(avatar, new LinearLayout.LayoutParams(dp(52), dp(52)));
            LinearLayout info = new LinearLayout(this); info.setOrientation(LinearLayout.VERTICAL); info.setPadding(dp(14), 0, 0, 0);
            TextView nameView = text(label, 17); nameView.setTypeface(null, Typeface.BOLD); nameView.setPadding(0, 0, 0, dp(3)); info.addView(nameView);
            JSONObject last = chat.optJSONObject("lastMessage");
            TextView preview = text(last == null ? "Открыть переписку" : last.optString("text", "Сообщение"), 14);
            preview.setTextColor(Color.rgb(112, 126, 141)); preview.setMaxLines(1); preview.setPadding(0, 0, 0, 0); info.addView(preview);
            row.addView(info, new LinearLayout.LayoutParams(0, -2, 1)); rows.addView(row);
            row.setOnClickListener(v -> openChat(id, label));
        }
        EditText idInput = field("ID чата (не номер телефона)", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        button(content, "Открыть по ID", () -> {
            try { long id = Long.parseLong(idInput.getText().toString().trim()); openChat(id, "Чат " + id); }
            catch (NumberFormatException e) { report("Введите числовой ID существующего чата."); }
        });
        button(content, "Подтверждение возраста — официальный MAX", this::digitalId);
        button(content, "Завершить локальную сессию", this::logout);
    }
    private void openChat(long id, String title) { chatId = id; chatTitle = title; loadHistory(); }
    private void loadHistory() {
        if (chatId == null || connection == null) return;
        final long requested = chatId;
        connection.call(49, json("chatId", requested, "from", System.currentTimeMillis(), "forward", 0,
            "backward", 50, "getMessages", true), p -> ui(() -> {
                if (chatId == null || chatId != requested) return;
                JSONArray messages = p.optJSONArray("messages");
                if (messages == null) { report("MAX не вернул историю в поддерживаемом формате."); return; }
                showMessages(messages);
            }));
    }
    private void showMessages(JSONArray messages) {
        content.removeAllViews(); content.addView(text(chatTitle, 22));
        LinearLayout controls = new LinearLayout(this); content.addView(controls);
        button(controls, "← Чаты", this::chatsScreen); button(controls, "Обновить", this::loadHistory);
        ScrollView scroll = new ScrollView(this); LinearLayout rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL); scroll.addView(rows);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        // MAX returns newest first for a backward history request.
        for (int i = messages.length() - 1; i >= 0; i--) {
            JSONObject msg = messages.optJSONObject(i); if (msg == null) continue;
            String value = msg.optString("text", "");
            if (value.isEmpty()) value = "[Вложение или служебное сообщение не поддерживается]";
            TextView bubble = text(msg.optString("sender", "") + "\n" + value, 16);
            bubble.setTextIsSelectable(true); bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
            boolean own = myUserId != Long.MIN_VALUE && msg.optLong("sender", Long.MAX_VALUE) == myUserId;
            bubble.setBackground(shape(own ? Color.rgb(218, 244, 213) : Color.WHITE, 16));
            bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels * 4 / 5);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, -2);
            bp.gravity = own ? Gravity.RIGHT : Gravity.LEFT; bp.setMargins(0, dp(4), 0, dp(4)); rows.addView(bubble, bp);
        }
        EditText input = field("Сообщение", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        button(content, "Отправить", () -> {
            if (busy) return;
            String value = input.getText().toString();
            if (value.trim().isEmpty() || value.length() > 4000) { report("Введите от 1 до 4000 символов."); return; }
            busy = true; status.setText("Отправка…");
            connection.call(64, Protocol.message(chatId, value), p -> ui(() -> {
                busy = false; input.setText(""); status.setText("MAX подтвердил запрос отправки."); loadHistory();
            }));
        });
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void privacy() {
        new AlertDialog.Builder(this).setTitle("MAXund 0.3 — экспериментальная версия")
            .setMessage("Независимый клиент на основе протокола nsdkinx/vkmax (MIT).\n\nРазрешение: только интернет. Нет SDK аналитики, загрузки контактов, записи экрана и работы сервиса в фоне. Переписка и токены не записываются в файлы. При сворачивании соединение закрывается.\n\nMAX получает содержимое переписки, номер, случайный ID сессии и сетевые метаданные. E2E-шифрование не реализовано. Совместимость с серверами не гарантирована; 2FA реализована экспериментально; звонки, вложения и уведомления не поддерживаются.\n\nЛокальный выход не отзывает сессию на сервере: её можно завершить в официальном клиенте MAX.")
            .setPositiveButton("Понятно", null).show();
    }
    private void logout() {
        qrTrack = null; handler.removeCallbacks(qrPoll);
        generation++; if (connection != null) connection.close(); connection = null;
        handler.removeCallbacks(keepalive); loginToken = null; smsToken = null; passwordTrack = null; myUserId = Long.MIN_VALUE;
        chats = new JSONArray(); chatId = null; busy = false;
        status.setText("Локальная сессия завершена. Серверную сессию можно отозвать в официальном MAX."); loginScreen();
    }
    @Override protected void onStart() {
        super.onStart(); foreground = true;
        if (loginToken != null && connection == null) {
            status.setText("Восстановление соединения…");
            connect(() -> connection.call(19, ProtocolLogin(), p -> ui(() -> signedIn(p))));
        } else if (passwordTrack != null && connection == null) {
            status.setText("Восстановление соединения для 2FA…");
            connect(this::passwordScreen);
        } else if (smsToken != null && connection == null) {
            status.setText("Восстановление соединения для ввода кода…");
            connect(this::codeScreen);
        }
    }
    @Override protected void onStop() {
        foreground = false; generation++; handler.removeCallbacks(keepalive); handler.removeCallbacks(qrPoll);
        if (qrTrack != null) { qrTrack = null; loginScreen(); }
        if (connection != null) connection.close(); connection = null; busy = false;
        status.setText("Соединение закрыто на время сворачивания.");
        super.onStop();
    }
    @Override protected void onDestroy() { logout(); super.onDestroy(); }
}
