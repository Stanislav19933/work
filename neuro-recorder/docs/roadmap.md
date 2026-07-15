# NeuroRecorder roadmap

Этот документ нужен, чтобы было понятно, почему приложение нельзя честно назвать готовым после одного шага разработки и что именно осталось сделать.

## Почему не всё сделано сразу

Исходное ТЗ описывает коммерческий Android-продукт с несколькими тяжёлыми локальными ML-подсистемами: запись, ASR, VAD, диаризация, определение имён, локальное саммари, экспорт и UI. Такие части нельзя безопасно «дописать одной правкой», потому что каждая из них влияет на сборку APK, размер приложения, память Pixel 7 Pro, скорость работы и стабильность GitHub Actions.

Правильный путь — делать этапами: после каждого этапа проект должен собираться, а изменения должны быть проверяемыми. Иначе легко получить большой набор файлов, который выглядит как готовое приложение, но не компилируется или падает на телефоне.

## Текущее состояние

| Блок | Статус | Комментарий |
| --- | --- | --- |
| Recorder | Готово для MVP | Есть фоновая запись WAV PCM 16 kHz mono; команды записи вынесены за `RecorderModule`. |
| Storage | Готово для MVP | Есть архив MediaStore, переименование, удаление и шаринг записи. |
| ASR | Готово для MVP | Основной путь — sherpa-onnx GigaAM CTC v2 через `AsrModule`; модели скачиваются и кэшируются на устройстве. |
| VAD | Готово для MVP | Silero VAD вынесен в отдельный `VadModule` и запускается перед ASR. |
| SpeakerDiarization | Готово для MVP | Добавлен sherpa-onnx diarization-модуль, сохранение сегментов и вкладка «По участникам». |
| NameRecognition | Готово для MVP | Контекстный модуль самопредставлений применяется к сегментам говорящих после диаризации. |
| Summary | Готово для MVP | Основной путь — локальная Gemma 3 1B int4 через MediaPipe LLM Inference; результат сохраняется через WorkManager. |
| Export | Готово для MVP | TXT/DOCX/PDF экспорт реализован локально и включает текст, участников и сохранённое саммари. |
| UI | Готово для MVP | Есть главный экран, архив, экран записи с вкладками «Аудио», «Текст», «По участникам», «Саммари» и экран моделей. |

## Ближайшие этапы

1. Провести финальную ручную проверку полного сценария на Android 14+/Pixel 7 Pro: запись → ASR/VAD → диаризация → имена → SLM-саммари → экспорт.
2. После успешной проверки скачать APK из GitHub Actions artifact и использовать как финальную сборку.

## Критерий «готово можно пользоваться»

Приложение можно будет назвать готовым, когда выполнены все условия:

- APK стабильно собирается в GitHub Actions.
- Запись работает в фоне на Android 14+.
- ASR, VAD, диаризация, определение имён и SLM-саммари работают локально на устройстве.
- Пользовательские записи и тексты не отправляются в облако.
- Есть экспорт TXT/DOCX/PDF.
- UI позволяет открыть запись и увидеть аудио, текст, участников и саммари.
- Приложение протестировано на реальном Pixel 7 Pro или максимально близком устройстве.


## Recent UX progress

- Model status screen: shows downloaded/missing local ASR, VAD, and diarization model files so the app remains understandable for non-developers. It can also download all required models immediately or clear cached model files.
- Build metadata: Android Gradle Plugin is set back to the current `8.13.0` line used by the workflow target.

- JVM tests in CI: GitHub Actions now runs `gradle --no-daemon test` before lint and APK artifact upload.

- WorkManager processing: transcription now runs through a persistent worker instead of an Activity-owned thread, with progress observed by the UI.

- Summary WorkManager processing: summary generation now runs through a persistent worker instead of an Activity-owned thread, so the UI can observe completion without blocking recording details.

- Local SLM summary: summary generation now uses Gemma 3 1B int4 through MediaPipe LLM Inference and downloads the model into the same local model cache as ASR/VAD/diarization.
