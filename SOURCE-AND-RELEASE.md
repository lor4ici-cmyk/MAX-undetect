# Исходники, сборка и публикация

Актуальная версия: **Undetect 2**, `26.34.1-undetect.2`, code 6857. [Изменения](UPDATE.md).
Предыдущая версия подтверждена владельцем успешным входом; серверные сценарии новой версии требуют новой проверки.

## Что доступно

[Полный архив Undetect 2, 99 МБ](https://gofile.io/d/A5qXbns0): `MAXund-Native-Undetect-2-full-source.zip`.
SHA-256: `2bae3b46ab840886b8349417055faa0ac1a5534f92c757eb9fbbc185e782851b`.
Исторический архив первой версии не содержит новых настроек Undetect 2.

В GitHub опубликованы все написанные для MAXund патчи, Java-код PrivacyLifecycle, сборщик, аудит, тесты и документы. Каталог `app/` — старый самостоятельный прототип, не код новой native-сборки.

Полный архив native-сборки содержит:

- `project/`: файлы проекта MAXund с патчами, сборщиком, тестами и документацией;
- `patched-native/`: подготовленное дерево AndroidManifest/res/assets/smali, включая скомпилированный в smali PrivacyLifecycle, ресурсы и исходные бинарные `.so` библиотеки; готовые DEX и промежуточный `build/` не включены.

**Это полное доступное декомпилированное дерево для пересборки APK, а не оригинальные Kotlin/Java/C++ исходники официального MAX. Исходников бинарных `.so` у нас нет.** Лицензия MIN на модификации не отменяет права на MAX и компоненты. Уведомления сохранены.

## Сборка проекта

Нужны Linux, Python 3 и доступ к загрузке закреплённых инструментов/базы. Скрипты рассчитаны на `/workspace/MAX-undetect` и `/workspace/toolchains`:

```bash
cd /workspace/MAX-undetect
python3 scripts/bootstrap_cloud.py
bash scripts/check-native.sh
```

Результат: `native/work/MAXund-native.apk`. Скрипт проверяет SHA-256 upstream и Apktool, применяет патчи, собирает все DEX, выравнивает и подписывает APK, выполняет аудит и четыре теста PrivacyLifecycle.

Для отдельной пересборки уже подготовленного дерева `patched-native/` нужен Apktool 2.12.0, Java 17 и Android build-tools 35.0.0:

```bash
java -Xmx4g -jar apktool_2.12.0.jar b patched-native -o unsigned.apk
zipalign -P 16 -f 4 unsigned.apk aligned.apk
apksigner sign --ks YOUR_PRIVATE_KEYSTORE --out MAXund.apk aligned.apk
apksigner verify --verbose MAXund.apk
```

Не применяйте `patch.py` или `undetect_patch.py` второй раз к `patched-native/`: патч рассчитан на чистую upstream-базу. Инструменты подписи должны быть в PATH; пароли вводите локально. Свой ключ храните приватно.

Публичный архив не содержит ключей подписи и данных аккаунта. С новым ключом подпись и хэш APK изменятся; обновление существующей установки возможно только с прежним ключом. Побайтовое совпадение APK не гарантируется.

## Как разместить Undetect 2 в GitHub Releases

1. Откройте https://github.com/lor4ici-cmyk/MAX-undetect/releases.
2. Нажмите **Create a new release**, tag `v26.34.1-maxund.2`, target `main`. Старый черновик `v26.34.1-maxund.1` относится к первой версии.
3. В описание вставьте сведения из `UPDATE.md`, название — `MAXund Native — Undetect 2`. Отметьте **pre-release**.
4. В поле **Attach binaries** перетащите APK, его `.sha256` и полный архив исходников. Дождитесь окончания загрузки.
5. Нажмите **Publish release**. Скопируйте ссылку на APK из раздела Assets.

128-МБ APK прикладывается к Releases, а не через **Add file → Upload files**. Исходники проекта уже доступны в разделе Code; **Code → Download ZIP** скачивает их, но upstream-дерево загружается отдельно сборщиком. Для готового полного дерева используйте прикреплённый полный архив.
