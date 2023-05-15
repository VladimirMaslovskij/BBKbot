package io.proj3ct.TestBlaBlaBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.proj3ct.TestBlaBlaBot.config.BotConfig;
import io.proj3ct.TestBlaBlaBot.config.MyGeocoder;
import io.proj3ct.TestBlaBlaBot.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVenue;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.Venue;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

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
    static final String NO_COMMENT = "noComment";
    private boolean isUserWriteLikePassenger = false;
    private boolean isUserWriteLikePassengerTo = false;
    private boolean isUserWriteLikePassengerWhen = false;
    private boolean isUserWriteLikeDriver = false;
    private boolean isUserWriteLikeDriverTo = false;
    private boolean isUserWriteLikeDriverWhen = false;
    private boolean isUserWriteLikeDriverPrice = false;
    private boolean isUserWriteLikeDriverHowMuchSits = false;
    private boolean isUserWriteLikeDriverAuto = false;
    private boolean isUserWriteLikeDriverComment = false;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveTripQuestionsRepository tripRepository;
    @Autowired
    private TripActiveRepository tripActiveRepository;


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
        listOfCommands.add(new BotCommand("/history", "Спланированные поездки."));
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
        if (isUserWriteLikeDriver) {
                driverLogicImplementation(update);
        } else if (isUserWriteLikePassenger) {
            passengerLogicImplementation(update);
        } else {
            if (update.hasMessage() && update.getMessage().hasText()) {
                botLogicIfUpdateIsText(update);
            } else if (update.hasCallbackQuery()) {
                botLogicIfUpdateIsCallback(update);
            }

        }
    }

    private void driverSkipComment(Update update) {
        if (update.getCallbackQuery().getData().equals(NO_COMMENT)) {
            Long tripId = checkTripId(update.getCallbackQuery().getMessage().getChatId());
            TripActive tripActive = getTripActive(tripId);
            tripActive.setActive(true);
            tripActive.setComment(null);
            tripActiveRepository.save(tripActive);
            isUserWriteLikeDriverTo = false;
            isUserWriteLikeDriverWhen = false;
            isUserWriteLikeDriver = false;
            isUserWriteLikeDriverPrice = false;
            isUserWriteLikeDriverHowMuchSits = false;
            isUserWriteLikeDriverAuto = false;
            isUserWriteLikeDriverComment = false;
            sendMessage(update.getCallbackQuery().getMessage().getChatId(),
                    "Ваша поездка спланирована, информацию о ней вы можете найти " +
                    "в меню, в разделе /history.");
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
        String hiAdminMesEmoji = "Здравствуйте, " + adminName + "!!!\n" +
                "В нашем боте:\n" + usersCount() + " пользователей;\n" +
                activeTripCount() + " активных поездок;\n" +
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
                .text("Все пользователи")
                .build();
        InlineKeyboardButton inlineKeyboardButtonShowActiveTrips = InlineKeyboardButton.builder()
                .callbackData(SHOW_ACTIVE_TRIPS)
                .text("Активные поездки")
                .build();
        InlineKeyboardButton inlineKeyboardButtonShowFinalTrips = InlineKeyboardButton.builder()
                .callbackData(SHOW_FINAL_TRIPS)
                .text("Завершенные поездки")
                .build();
        InlineKeyboardButton inlineKeyboardButtonStartTrip = InlineKeyboardButton.builder()
                .callbackData(START_TRIP)
                .text("Новая поездка")
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

    private void driverLogicImplementation(Update update) {
        if (update.hasCallbackQuery()) {
            driverSkipComment(update);
        } else {
            var chatId = update.getMessage().getChatId();
            var text = update.getMessage().getText();
            var tripId = checkTripId(chatId);
            if (!isUserWriteLikeDriverTo && !isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
                    !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverAuto && !isUserWriteLikeDriverComment) {
                Location location = update.getMessage().getLocation();
                String address = createLocationFromCoordinates(chatId, location);
                String mes = "Вы выезжаете из : " + address;
                sendMessage(chatId, mes);
                TripActive trip = new TripActive();
                trip.setLongFrom(location.getLongitude());
                trip.setLatFrom(location.getLatitude());
                trip.setTripId(tripId);
                trip.setActive(false);
                trip.setDriver(chatId);
                trip.setCityFrom(address);
                trip.setCityTo("some");
                trip.setTripDate("27.04.2099/15:00");
                trip.setTripPrice(100);
                trip.setCountOfSits(2);
                isUserWriteLikeDriverTo = true;
                tripActiveRepository.save(trip);
                sendMessage(chatId, "Введите куда вы поедете:");
            } else if (!isUserWriteLikeDriverWhen && isUserWriteLikeDriverTo && !isUserWriteLikeDriverPrice &&
                    !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverAuto && !isUserWriteLikeDriverComment) {
                Location location = update.getMessage().getLocation();
                String address = createLocationFromCoordinates(chatId, location);
                String mes = "Вы едете в : " + address;
                sendMessage(chatId, mes);
                TripActive trip = getTripActive(tripId);
                trip.setLongTo(location.getLongitude());
                trip.setLatTo(location.getLatitude());
                trip.setCityTo(address);
                tripActiveRepository.save(trip);
                sendMessage(update.getMessage().getChatId(), "Введите дату и время поездки в формате " +
                        "01.01.2023/05:00");
                isUserWriteLikeDriverWhen = true;
            } else if (isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
                    !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverAuto && !isUserWriteLikeDriverComment) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy/HH:mm");
                Date newDate;
                try {
                    newDate = dateFormat.parse(text);
                } catch (ParseException e) {
                    sendMessage(chatId, "Введен неверный формат даты, попробуйте еще раз.");
                    throw new RuntimeException(e);
                }
                if (new Date().getTime() > newDate.getTime()) {
                    sendMessage(chatId, "Нельзя вводить уже прошедшую дату, попробуйте еще раз.");
                } else {
                    String mes = "Выбрана дата и время поездки: ";
                    sendMessage(chatId, mes);
                    sendMessage(chatId, text);
                    TripActive trip = getTripActive(tripId);
                    trip.setTripDate(text);
                    tripActiveRepository.save(trip);
                    isUserWriteLikeDriverAuto = true;
                    sendMessage(chatId, "Введите пожалуйста название Вашего автомобиля");
                }
            } else if (isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
                    !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverComment && isUserWriteLikeDriverAuto) {
                String mes = "Ваш автомобиль: ";
                sendMessage(chatId, mes);
                sendMessage(chatId, text);
                TripActive trip = getTripActive(tripId);
                trip.setAuto(text);
                tripActiveRepository.save(trip);
                isUserWriteLikeDriverHowMuchSits = true;
                sendMessage(chatId, "Введите количество свободных мест (не более 8): ");
            } else if (isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
                    isUserWriteLikeDriverHowMuchSits && isUserWriteLikeDriverAuto && !isUserWriteLikeDriverComment) {
                if ((Integer.parseInt(text) < 1)
                        || (Integer.parseInt(text) > 8)) {
                    sendMessage(chatId, "Количество возможных мест может быть не менее 1 и не более 8" +
                            "Пожалуйста, введите количество возможных мест еще раз.");
                }
                String mes = "Выбрано следующее количество свободных мест: " + text;
                sendMessage(chatId, mes);
                TripActive trip = getTripActive(tripId);
                trip.setCountOfSits(Integer.parseInt(text));
                tripActiveRepository.save(trip);
                sendMessage(chatId, "Введите стоимость проезда для одного человека (в рублях): ");
                isUserWriteLikeDriverPrice = true;
            } else if (isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && isUserWriteLikeDriverPrice &&
                    isUserWriteLikeDriverHowMuchSits && isUserWriteLikeDriverAuto && !isUserWriteLikeDriverComment) {
                if ((Integer.parseInt(text) < 1)
                        || (Integer.parseInt(text) > 100000)) {
                    sendMessage(chatId, "Стоимость поездки не может быть менее 0 рублей и более 100 000 рублей \n" +
                            "Пожалуйста, введите стоимость поездки еще раз.");
                }
                String mes = "Установлена стоимость проезда для одного человека: " + text;
                sendMessage(chatId, mes);
                TripActive trip = getTripActive(tripId);
                trip.setTripPrice(Integer.parseInt(text));
                tripActiveRepository.save(trip);
                isUserWriteLikeDriverComment = true;
                String finalMes = "Введите комментарий к поездке, например: не курить в авто, " +
                        "без животных и т.д., либо нажмите \"Пропустить\"";
                SendMessage sendMessage = new SendMessage();
                sendMessage.setText(finalMes);
                sendMessage.setChatId(chatId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(NO_COMMENT)
                        .text("Пропустить")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                executeMessage(sendMessage);
            } else if (isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && isUserWriteLikeDriverPrice &&
                    isUserWriteLikeDriverHowMuchSits && isUserWriteLikeDriverAuto && isUserWriteLikeDriverComment) {
                TripActive trip = getTripActive(tripId);
                trip.setComment(text);
                trip.setActive(true);
                tripActiveRepository.save(trip);
                isUserWriteLikeDriverTo = false;
                isUserWriteLikeDriverWhen = false;
                isUserWriteLikeDriver = false;
                isUserWriteLikeDriverPrice = false;
                isUserWriteLikeDriverHowMuchSits = false;
                isUserWriteLikeDriverAuto = false;
                isUserWriteLikeDriverComment = false;
                sendMessage(chatId, "Ваша поездка спланирована, информацию о ней вы можете найти " +
                        "в меню, в разделе /history.");
            }
        }
    }

    private void passengerLogicImplementation(Update update) {
        Long chatId = update.getMessage().getChatId();
        var tripId = checkQuestionId(chatId);
        String text = update.getMessage().getText();
            if (!isUserWriteLikePassengerTo && !isUserWriteLikePassengerWhen) {
                Location location = update.getMessage().getLocation();
                String address = createLocationFromCoordinates(chatId, location);
                String mes = "Ищем поездку из: " + address;
                sendMessage(chatId, mes);
                ActiveTripQuestions trip = new ActiveTripQuestions();
                trip.setTripId(tripId);
                trip.setPassengerId(chatId);
                trip.setLongFrom(location.getLongitude());
                trip.setLatFrom(location.getLatitude());
                trip.setCityFrom(address);
                trip.setActive(false);
                trip.setCityTo("SomeCity");
                trip.setDateFormat("01.01.1991");
                tripRepository.save(trip);
                isUserWriteLikePassengerTo = true;
                sendMessage(chatId, "Введите куда вы поедете:");
            } else if (!isUserWriteLikePassengerWhen && isUserWriteLikePassengerTo) {
                Location location = update.getMessage().getLocation();
                String address = createLocationFromCoordinates(chatId, location);
                String mes = "Ищем поездку в: " + address;
                sendMessage(chatId, mes);
                ActiveTripQuestions trip = getActiveQuestion(tripId);
                trip.setCityTo(address);
                trip.setLongTo(location.getLongitude());
                trip.setLatTo(location.getLatitude());
                tripRepository.save(trip);
                sendMessage(chatId, "Введите дату поездки в формате " +
                        "01.01.2023");
                isUserWriteLikePassengerWhen = true;
            } else if (isUserWriteLikePassengerTo && isUserWriteLikePassengerWhen) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
                Date newDate;
                try {
                    newDate = dateFormat.parse(text);
                } catch (ParseException e) {
                    sendMessage(chatId, "Введен неверный формат даты, попробуйте еще раз.");
                    throw new RuntimeException(e);
                }
                if (new Date().getTime() > newDate.getTime() + 86400000) {
                    sendMessage(chatId, "Нельзя вводить уже прошедшую дату, попробуйте еще раз.");
                } else {
                    String mes = "Ваша поездка запланирована на:";
                    sendMessage(chatId, mes);
                    sendMessage(chatId, text);
                    ActiveTripQuestions trip = getActiveQuestion(tripId);
                    String date = text;
                    trip.setDateFormat(date);
                    trip.setActive(true);
                    tripRepository.save(trip);
                    isUserWriteLikePassengerTo = false;
                    isUserWriteLikePassengerWhen = false;
                    isUserWriteLikePassenger = false;
                }
            }
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
        StringBuilder str = new StringBuilder();
        for (User user : users) {
            i++;
            str.append(i + ". Имя - " + user.getFirstName() + ", Логин - @" +
                    user.getUserName() + "\n");
        }
        String message = String.valueOf(str);
        sendMessage(chatId, message);
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
            user.setBan(false);
            user.setWhite(false);

            userRepository.save(user);
            log.info("user saved: " + user);
        }
    }

    // Формирует и отправляет приветственное сообщение
    private void startCommandReceived(long chatId, String name) {
        if (!isUserBanned(chatId)) {
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
        } else sendMessage(chatId, "Ваш профиль забанен в данном боте администрацией.\n" +
                "Чтобы узнать причины бана, либо оспорить решение администрации, обратитесь к:\n" +
                "@kl_ms или @vladimir_816.");
    }

    // Возвращает историю поездок пользователя
    private void getPassengerHistory(long chatId) {
        Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
        String answer = "Ваши заявки на поиск поездки:\n";
        sendMessage(chatId, answer);
        for (ActiveTripQuestions trip : trips) {
            if (trip.getPassengerId().equals(chatId)) {
                sendMessage(chatId, trip.getTripInfo());
            }
        }
        log.info("Request a history of trips");
    }
    private void getDriverHistory(long chatId) {
        Iterable<TripActive> trips = tripActiveRepository.findAll();
        String answer = "Ваши спланированные поездки:\n";
        sendMessage(chatId, answer);
        for (TripActive trip : trips) {
            if (trip.getDriver().equals(chatId)) {
                sendMessage(chatId, trip.getTripInfo());
            }
        }
        log.info("Request a history of trips");
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
    private TripActive getTripActive(Long id) {
        Optional<TripActive> trips = tripActiveRepository.findById(id);
        TripActive tripActive = new TripActive();
        if (trips.isPresent()) {
            tripActive = trips.get();
        }
        return tripActive;
    }
    private ActiveTripQuestions getActiveQuestion(Long id) {
        Optional<ActiveTripQuestions> trips = tripRepository.findById(id);
        ActiveTripQuestions tripActive = new ActiveTripQuestions();
        if (trips.isPresent()) {
            tripActive = trips.get();
        }
        return tripActive;
    }

    // Этот метод будет при формировании поездки водителем придавать id маршрута из файла whitelist.txt
    // объекту TripActive и вносить в таблицу этот id
    // Для пассажира метод делает то же самое, при формировании запроса - задает объекту
    // ActiveTripQuestion id маршрута, который он ищет
    // Если null - то предлагает водителю указать в коментарии через какие населенные пункты он поедет.


    private String findUserNameById(Long id) {
        Optional<User> users = userRepository.findById(id);
        if (users.isPresent()) {
            User user = users.get();
            return user.getUserName();
        }
        return null;
    }
    private String createLocationFromCoordinates(Long chatId, Location location) {
        MyGeocoder geocoder = new MyGeocoder();
        String address = null;
        try {
            address = geocoder.sendGeo(location.getLongitude(), location.getLatitude());
        } catch (IOException e) {
            sendMessage(chatId, "Не получается определить это место, попробуйте еще раз.");
            throw new RuntimeException(e);
        }
        return address;
    }
    private void botLogicIfUpdateIsText(Update update) {
        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        if (messageText.contains("/send") && (checkAdmin(chatId))) {
            tripActiveRepository.deleteById(13133591553L);
            tripActiveRepository.deleteById(13133591554L);
//            var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")));
//            var users = userRepository.findAll();
//            for (User user : users) {
//                sendMessage(user.getCharId(), textToSend);
//            }
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
                    getPassengerHistory(chatId);
                    getDriverHistory(chatId);
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
    private void botLogicIfUpdateIsCallback(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (callbackData.equals(DRIVER)) {
            if ((checkTripId(chatId) != null)) {
                String text = "Начинаем составлять поездку.";
                executeEditMessageText(text, chatId, messageId);
                sendMessage(chatId, "Введите, откуда вы поедете:");
                isUserWriteLikeDriver = true;
            } else sendMessage(chatId, "У вас превышено количество активных поездок.\n" +
                    "Для повышения допустимого количества активных поездок обратитесь к администрации бота: " +
                    "@kl_ms или @vladimir_816.");
        } else if (callbackData.equals(PASSENGER)) {
            if ((checkQuestionId(chatId) != null) || isUserInWhiteList(chatId)) {
                String text = "Начинаем составлять запрос на поездку:";
                executeEditMessageText(text, chatId, messageId);
                sendMessage(chatId, "Введите, откуда вы хотите поехать:");
                isUserWriteLikePassenger = true;
            } else sendMessage(chatId, "У вас превышено количество активных запросов на поездки.\n" +
                    "Для повышения допустимого количества активных запросов обратитесь к администрации бота:\n" +
                    "@kl_ms или @vladimir_816.");
        } else if (callbackData.equals(SHOW_ALL_USERS) && (checkAdmin(chatId))) {
            usersSOUT(chatId);
        } else if (callbackData.equals(SHOW_FINAL_TRIPS) && (checkAdmin(chatId))) {
            String message = "Завершенные поездки отсутствуют";
            sendMessage(chatId, message);
        } else if (callbackData.equals(SHOW_ACTIVE_TRIPS) && (checkAdmin(chatId))) {
            String message = "Активные поездки отсутствуют";
            sendMessage(chatId, message);
        } else if (callbackData.equals(START_TRIP) && (checkAdmin(chatId))) {
            String text = EmojiParser.parseToUnicode("Уважаемый шеф, " + ":sunglasses:"
                    + " вы сегодня за рулем или на диванчике сзади?");
            EditMessageText message = new EditMessageText();
            message.setChatId(chatId);
            message.setText(text);
            message.setMessageId((int) messageId);
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
            try {
                execute(message);
            } catch (TelegramApiException e) {
                log.error(ERROR_TEXT + e.getMessage());
            }
        }
    }
    private Long checkTripId(Long chatId) {
        int maxTrips;
        if (isUserInWhiteList(chatId)) {
            maxTrips = 20;
        }
        else {
            maxTrips = 5;
        }
        Long counter = 1L;
        List<Long> ids = new ArrayList<>();
        chatId = chatId * 10;
        Long result = chatId;
        for (int i = 0; i < maxTrips; i++) {
            chatId++;
            ids.add(chatId);
        }
        Iterable<TripActive> trips = tripActiveRepository.findAllById(ids);
        for (TripActive trip : trips) {
            if (trip.isActive()) {
                counter++;
                ids.remove(trip.getTripId());
            }
        }
        if (((result + counter > result) && (result + counter <= chatId)) || (counter == 1L))
            return ids.get(0);
        else return null;
    }
    private Long checkQuestionId(Long chatId) {
        int maxTrips;
        if (isUserInWhiteList(chatId)) {
            maxTrips = 20;
        }
        else {
            maxTrips = 5;
        }
        Long counter = 1L;
        List<Long> ids = new ArrayList<>();
        chatId = chatId * 10;
        Long result = chatId;
        for (int i = 0; i < maxTrips; i++) {
            chatId++;
            ids.add(chatId);
        }
        Iterable<ActiveTripQuestions> trips = tripRepository.findAllById(ids);
        for (ActiveTripQuestions trip : trips) {
            if (trip.isActive())
                counter++;
        }
        if (((result + counter > result) && (result + counter <= chatId)) || (counter == 1L))
            return result + counter;
        else return null;
    }
    private void addUserToWhiteLIst(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setWhite(true);
            userRepository.save(user);
        }
    }
    private void banUser(Long userId) {
        Optional<User> userOpt = userRepository.findById(userId);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            user.setBan(true);
            userRepository.save(user);
        }
    }
    private boolean isUserBanned(Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            return user.isBan();
        }
        return false;
    }
    private boolean isUserInWhiteList(Long id) {
        Optional<User> userOpt = userRepository.findById(id);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
            return user.isWhite();
        }
        return false;
    }
}
