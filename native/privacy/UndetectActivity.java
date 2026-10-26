package app.maxund.privacy;
import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;

public final class UndetectActivity extends Activity {
    private LinearLayout body;
    public void onCreate(Bundle state) {
        super.onCreate(state);setTitle("Undetect");Undetect.install(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setOrientation(1);
        int pad=(int)(20*getResources().getDisplayMetrics().density);body.setPadding(pad,pad,pad,pad);
        scroll.addView(body);setContentView(scroll);
        Button back=new Button(this);back.setText("‹ Назад в MAXund");back.setOnClickListener(v->finish());body.addView(back);
        text("Undetect · версия 26.34.1-undetect.2",22);
        text("Функции протокола экспериментальные. Инкогнито не скрывает вас от сервера MAX и не гарантирует статус «не в сети».",14);
        toggle("incognito","Инкогнито","Объединяет скрытие интерактивности, прочтения, набора текста и записи. Исключения для чатов сохраняются.");
        toggle("online","Скрывать активность","Отправлять interactive=false. Сервер может определять онлайн другими способами. При включении переподключитесь к аккаунту обычным перезапуском приложения, без выхода из аккаунта.");
        toggle("reads","Не отправлять прочтение","Блокировать READ_MESSAGE. Локальный счётчик непрочитанных может сохраняться. Ранее отправленные отметки не отменяются.");
        toggle("typing","Скрывать набор текста","Блокировать исходящие сигналы MSG_TYPING для текста и других невокальных действий.");
        toggle("recording","Скрывать запись голосового","Блокировать сигналы MSG_TYPING типов AUDIO/VOICE/VIDEO. Фактические голосовые сообщения по-прежнему отправляются.");
        Button manual=new Button(this);manual.setText("Отправить прочтение вручную…");manual.setOnClickListener(v->choose(false));body.addView(manual);
        Button exceptions=new Button(this);exceptions.setText("Исключения для чатов…");exceptions.setOnClickListener(v->choose(true));body.addView(exceptions);
        text("Список заполняется при блокировке прочтения в открытых чатах. Чаты обозначены внутренним номером; названия и тексты сообщений не сохраняются. Исключения относятся к прочтению и индикаторам, но не к глобальному онлайн.",14);
        toggle("calls","Подтверждать исходящий звонок","Дополнительное подтверждение личных и групповых голосовых/видеозвонков. Входящие вызовы не блокируются; другие маршруты звонков не проверены.");
        toggle("lock","Блокировать при уходе в фон","Разблокировка биометрией или кодом блокировки телефона. Сначала настройте системную блокировку Android.");
        toggle("notifications","Скрывать текст уведомлений","Убирать текст, историю, действия и нестандартные превью из новых уведомлений. Уже показанные уведомления не меняются.");
        toggle("autoload","Отключать автозагрузку медиа","Пропускать задачи DownloadAttachesWorker с причиной AUTOLOAD. Загрузка по нажатию сохраняется; другие загрузчики отдельно не проверены.");
        text("Камера и микрофон: политика отзыва разрешений после ухода в фон сохранена. Фоновые звонки прекращаются. Все новые переключатели по умолчанию выключены.",14);
    }
    private void text(String value,int size) { TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setPadding(0,12,0,12);body.addView(t); }
    private void toggle(String key,String title,String detail) {
        Switch s=new Switch(this);s.setText(title);s.setTextSize(17);s.setChecked(Undetect.enabled(key));body.addView(s);text(detail,13);
        s.setOnCheckedChangeListener((b,on)-> {
            if(key.equals("lock") && on && !((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isDeviceSecure()) {
                s.setChecked(false);Toast.makeText(this,"Сначала настройте блокировку телефона в Android",1).show();return;
            }
            Undetect.set(key,on);
            if(key.equals("lock") && on) { Undetect.unlocked=false;startActivity(new android.content.Intent(this,UndetectLockActivity.class)); }
        });
    }
    private void choose(boolean exception) {
        java.util.Set<String> all=new java.util.LinkedHashSet<>(java.util.Arrays.asList(Undetect.pendingIds()));all.addAll(Undetect.exceptions());
        String[] ids=all.toArray(new String[0]);
        if(ids.length==0) { Toast.makeText(this,"Сначала включите скрытие прочтения и откройте чат с новым сообщением",1).show();return; }
        String[] titles=new String[ids.length];boolean[] checked=new boolean[ids.length];
        for(int i=0;i<ids.length;i++) { titles[i]="Чат №"+ids[i];checked[i]=Undetect.exceptions().contains(ids[i]); }
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(exception?"Разрешать сигналы в чатах":"Отправить накопленное прочтение");
        if(exception) dialog.setMultiChoiceItems(titles,checked,(d,w,on)->Undetect.exception(ids[w],on)).setPositiveButton("Готово",null);
        else dialog.setItems(titles,(d,w)->new AlertDialog.Builder(this).setTitle("Отправить прочтение?")
            .setMessage("Собеседник сможет увидеть, что сообщение прочитано. Отменить это нельзя.")
            .setNegativeButton("Отмена",null).setPositiveButton("Отправить",(dd,ww)->Toast.makeText(this,Undetect.sendRead(ids[w])?"Запрос передан клиенту; подтверждение сервера не проверено":"Запрос недоступен. Откройте чат ещё раз.",1).show()).show());
        dialog.setNegativeButton("Закрыть",null).show();
    }
}
