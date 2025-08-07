import os
import telebot
from flask import Flask, request

BOT_TOKEN = os.getenv("BOT_TOKEN")
bot = telebot.TeleBot(BOT_TOKEN)

app = Flask(__name__)

# Принимает сообщения от Telegram
@app.route(f"/{BOT_TOKEN}", methods=["POST"])
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
