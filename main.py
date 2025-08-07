import os
import telebot
from flask import Flask, request

BOT_TOKEN = os.getenv("BOT_TOKEN")
bot = telebot.TeleBot(BOT_TOKEN)

app = Flask(__name__)

# Принимает сообщения от Telegram
@app.route("/send_file", methods=["POST"])
def telegram_webhook():
    update = telebot.types.Update.de_json(request.stream.read().decode("utf-8"))
    bot.process_new_updates([update])
    return "OK", 200

# Стартовая команда
@bot.message_handler(commands=["start"])
def send_welcome(message):
    bot.send_message(message.chat.id, "Бот готов принимать файлы!")

# Любой файл
@bot.message_handler(content_types=["document", "audio", "video", "photo"])
def handle_file(message):
    bot.send_message(message.chat.id, "Файл получен, пересылаю...")
    bot.send_document(message.chat.id, message.document.file_id)

# Старт Flask-сервера
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 5000)))

@app.route("/upload", methods=["POST"])
def manual_upload():
    file = request.files.get("file")
    if file:
        chat_id = os.getenv("696743488")  # Укажи свой Chat ID в переменных окружения
        bot.send_message(chat_id, "Файл получен вручную, пересылаю...")
        bot.send_document(chat_id, file)
        return "Файл отправлен", 200
    return "Файл не найден", 400
