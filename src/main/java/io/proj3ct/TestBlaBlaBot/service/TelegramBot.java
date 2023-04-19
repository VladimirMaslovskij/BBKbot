package io.proj3ct.TestBlaBlaBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.proj3ct.TestBlaBlaBot.config.BotConfig;
import io.proj3ct.TestBlaBlaBot.model.User;
import io.proj3ct.TestBlaBlaBot.model.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    final BotConfig config;
    static final String DRIVER = "driver";
    static final String PASSENGER = "passenger";
    static final String SHOW_ALL_USERS = "showAllUsers";
    static final String SHOW_FINAL_TRIPS = "showFinalTrips";
    static final String SHOW_ACTIVE_TRIPS = "showActiveTrips";
    static final String START_TRIP = "startTrip";
    static final String ERROR_TEXT = "Error occurred: ";
    @Autowired
    private UserRepository userRepository;

    static final String HELP_TEXT = "Данный бот используется для поиска попутчиков водителями, и наоборот.\n" +
            "     Для водителя необходимо:\n" +
            "1. В меню бота выбрать новую поездку (/start). Далее - вариант \"Водитель\" \n" +
            "2. Указать место отправления, место прибытия," +
            " количество пассажиров, а также дату и время планируемой поездки.\n" +
            "3. Внести оплату для формирования Вашей заявки в базу данных водителей.\n" +
            "4. Ожидать ответа от бота, когда найдутся ваши попутчики. \n" +
            "     Для пассажира необходимо:\n" +
            "1. В меню бота выбрать новую поездку (/start). Далее - вариант \"Пассажир\" \n" +
            "2. Выбрать место отправления, место прибытия, а также дату.\n" +
            "3. Ожидать ответа от бота с вариантами возможных поездок.\n";

    public TelegramBot(BotConfig config) {
        this.config = config;
        List<BotCommand> listOfCommands = new ArrayList<>();
        listOfCommands.add(new BotCommand("/start", "Новая поездка."));
        listOfCommands.add(new BotCommand("/history", "История моих поездок."));
        listOfCommands.add(new BotCommand("/help", "Инструкция по использованию бота."));
        try {
            this.execute(new SetMyCommands(listOfCommands, new BotCommandScopeDefault(), null));
        }
        catch (TelegramApiException e) {
            log.error("Error setting bot's command list" + e.getMessage());
        }
    }

    // Реагирует на нажатие кнопки или написание команды юзером, а точнее
    // на отправку юзером на сервер объекта Update (см. документацию telegrambots)
    @Override
    public void onUpdateReceived(Update update) {
        if(update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (messageText.contains("/send") && (checkAdmin(chatId))) {
                var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
                var users = userRepository.findAll();
                for (User user : users) {
                    sendMessage(user.getCharId(), textToSend);
                }
            } else {

                switch (messageText) {
                    case "/start":
                        if (checkAdmin(chatId)) {
                            adminMessage(chatId);
                        } else {
                            registerUser(update.getMessage()); // Регистрирует юзера при первом его входе в бота (/start)
                            startCommandReceived(chatId, update.getMessage().getChat().getFirstName()); // Выводит приветствие
                        }
                        break;
                    case "/history":
                        getHistory(chatId);
                        break;
                    case "/help":
                        sendMessage(chatId, HELP_TEXT);
                        log.info("Request info");
                        break;
                    default:
                        sendMessage(chatId, "Извините, такой команды не существует.");
                }
            }
        }
        else if(update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long messageId = update.getCallbackQuery().getMessage().getMessageId();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if(callbackData.equals(DRIVER)) {
                String text = "Ебать, ты Ярик водила???!";
                executeEditMessageText(text, chatId, messageId);
            }
            else if(callbackData.equals(PASSENGER)) {
                String text = "Попутчик, хуй тебе голубчик";
                executeEditMessageText(text, chatId, messageId);
            }
            else if(callbackData.equals(SHOW_ALL_USERS) && (checkAdmin(chatId))) {
                usersSOUT(chatId);
            }
            else if(callbackData.equals(SHOW_FINAL_TRIPS) && (checkAdmin(chatId))) {
                String message = "Завершенные поездки отсутствуют";
                sendMessage(chatId, message);
            }
            else if(callbackData.equals(SHOW_ACTIVE_TRIPS) && (checkAdmin(chatId))) {
                String message = "Активные поездки отсутствуют";
                sendMessage(chatId, message);
            }
            else if(callbackData.equals(START_TRIP) && (checkAdmin(chatId))) {
                String text = EmojiParser.parseToUnicode("Уважаемый пиздатый шеф, " + ":sunglasses:"
                        + " вы сегодня за рулем или на диванчике сзади?");
                EditMessageText message = new EditMessageText();
                message.setChatId(chatId);
                message.setText(text);
                message.setMessageId((int)messageId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> row = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonDriver = InlineKeyboardButton.builder()
                        .callbackData(DRIVER)
                        .text("Водитель")
                        .build();
                InlineKeyboardButton inlineKeyboardButtonPassenger = InlineKeyboardButton.builder()
                        .callbackData(PASSENGER)
                        .text("Попутчик")
                        .build();
                row.add(inlineKeyboardButtonDriver);
                row.add(inlineKeyboardButtonPassenger);
                rowsLine.add(row);
                markup.setKeyboard(rowsLine);
                message.setReplyMarkup(markup);
                try{
                    execute(message);
                }
                catch (TelegramApiException e) {
                    log.error(ERROR_TEXT + e.getMessage());
                }
            }
        }

    }

    private boolean checkAdmin(long chatId) {
        return (chatId == 1313359155 || chatId == 401930223);
    }

    private void adminMessage(long chatId) {
        String adminName;
        if (chatId == 1313359155)
            adminName = "Владимир Сергеевич";
        else adminName = "Иван Андреевич";
        String hiAdminMesEmoji = "Здравствуйте, " + adminName + "!!!\n " +
                "В нашем боте:\n" + usersCount() + " пользователей;\n" +
                activeTripCount() + " активных поездок;\n " +
                finalTripCount() + " завершенных поездок.";
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(hiAdminMesEmoji);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> upRow = new ArrayList<>();
        List<InlineKeyboardButton> downRow = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButtonShowUsers = InlineKeyboardButton.builder()
                .callbackData(SHOW_ALL_USERS)
                .text("Показать всех пользователей")
                .build();
        InlineKeyboardButton inlineKeyboardButtonShowActiveTrips = InlineKeyboardButton.builder()
                .callbackData(SHOW_ACTIVE_TRIPS)
                .text("Показать активные поездки")
                .build();
        InlineKeyboardButton inlineKeyboardButtonShowFinalTrips = InlineKeyboardButton.builder()
                .callbackData(SHOW_FINAL_TRIPS)
                .text("Показать завершенные поездки")
                .build();
        InlineKeyboardButton inlineKeyboardButtonStartTrip = InlineKeyboardButton.builder()
                .callbackData(START_TRIP)
                .text("Создать/Найти поездку")
                .build();
        upRow.add(inlineKeyboardButtonShowUsers);
        upRow.add(inlineKeyboardButtonShowActiveTrips);
        rowsLine.add(upRow);
        downRow.add(inlineKeyboardButtonShowFinalTrips);
        downRow.add(inlineKeyboardButtonStartTrip);
        rowsLine.add(downRow);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }
    private int usersCount() {
        Iterable<User> users = userRepository.findAll();
        int i = 0;
        for (User ignored : users) {
            i++;
        }
        return i;
    }
    private int activeTripCount () {
        return 0;
    }
    private int finalTripCount() {
        return 0;
    }
    private void usersSOUT(long chatId) {
        Iterable<User> users = userRepository.findAll();
        int i = 0;
        for (User user : users) {
            i++;
            String message = i + ". Имя - " + user.getFirstName() + ", Логин - @" +
                    user.getUserName();
            sendMessage(chatId, message);
        }
        String usersCount = "Всего пользователей в боте - " + i;
        sendMessage(chatId, usersCount);
    }

    // Происходит регистрация юзера
    private void registerUser(Message message) {
        if(userRepository.findById(message.getChatId()).isEmpty()) { // Проверяем, существует ли уже такой пользователь
            var chatId = message.getChatId();
            var chat = message.getChat();
            User user = new User();
            user.setCharId(chatId);
            user.setFirstName(chat.getFirstName());
            user.setLastName(chat.getLastName());
            user.setUserName(chat.getUserName());
            user.setRegisteredAt(new Timestamp(System.currentTimeMillis()));

            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    // Формирует и отправляет приветственное сообщение
    private void startCommandReceived(long chatId, String name) {
        String answer = "Привет, " + name + "! Ты сегодня водитель или попутчик?";
        log.info("Replied to user " + name);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(answer);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButtonDriver = InlineKeyboardButton.builder()
                .callbackData("driver")
                .text("Водитель")
                .build();
        InlineKeyboardButton inlineKeyboardButtonPassenger = InlineKeyboardButton.builder()
                .callbackData("passenger")
                .text("Попутчик")
                .build();
        row.add(inlineKeyboardButtonDriver);
        row.add(inlineKeyboardButtonPassenger);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    // Возвращает историю поездок пользователя
    private void getHistory(long chatId) {
        String answer = "Ваша история позедок пуста.";
        log.info("Request a history of trips");
        sendMessage(chatId, answer);
    }
    // Отправляет сообщения, первый параметр - id чата между ботом и пользователем, второй - сообщение
    private void sendMessage(long chatId, String textToSend) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        executeMessage(message);
    }


    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    public String getBotToken() {
        return config.getToken();
    }
    private void executeEditMessageText(String text, long chatId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText(text);
        message.setMessageId((int)messageId);
        try{
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }
    private void executeMessage(SendMessage message) {
        try{
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }
}
