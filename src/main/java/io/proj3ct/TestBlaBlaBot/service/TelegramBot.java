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
    private boolean isUserWriteLikePassenger = false;
    private boolean isUserWriteLikePassengerTo = false;
    private boolean isUserWriteLikePassengerWhen = false;
    private boolean isUserWriteLikeDriver = false;
    private boolean isUserWriteLikeDriverTo = false;
    private boolean isUserWriteLikeDriverWhen = false;
    private boolean isUserWriteLikeDriverPrice = false;
    private boolean isUserWriteLikeDriverHowMuchSits = false;
    private boolean isUserWriteLikeDriverAuto = false;
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
        if (isUserWriteLikeDriver) {
            driverLogicImplementation(update);
        } else if (isUserWriteLikePassenger) {
            passengerLogicImplementation(update);
        } else {
            if (update.hasMessage() && update.getMessage().hasText()) {
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
            } else if (update.hasCallbackQuery()) {
                String callbackData = update.getCallbackQuery().getData();
                long messageId = update.getCallbackQuery().getMessage().getMessageId();
                long chatId = update.getCallbackQuery().getMessage().getChatId();

                if (callbackData.equals(DRIVER)) {
                    String text = "Начинаем составлять поездку.";
                    executeEditMessageText(text, chatId, messageId);
                    sendMessage(chatId, "Введите, откуда вы поедете:");
                    isUserWriteLikeDriver = true;
                } else if (callbackData.equals(PASSENGER)) {
                    String text = "Начинаем составлять запрос на поездку:";
                    executeEditMessageText(text, chatId, messageId);
                    sendMessage(chatId, "Введите, откуда вы хотите поехать:");
                    isUserWriteLikePassenger = true;
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

    private void driverLogicImplementation(Update update) {
        var chatId = update.getMessage().getChatId();
        var text = update.getMessage().getText();
        String id = String.valueOf(chatId);
        id = new StringBuilder(id).reverse().toString();
        var tripId = Long.valueOf(id);
        if (!isUserWriteLikeDriverTo && !isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
        !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverAuto) {
            Location location = update.getMessage().getLocation();
            String address = createLocationFromCoordinates(chatId, location);
            String mes = "Вы выезжаете из : " + address;
            sendMessage(chatId, mes);
            TripActive trip = new TripActive();
            trip.setLongFrom(location.getLongitude());
            trip.setLatFrom(location.getLatitude());
            trip.setTripId(tripId);
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
                !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverAuto) {
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
                !isUserWriteLikeDriverHowMuchSits && !isUserWriteLikeDriverAuto) {
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
        } else if(isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
                !isUserWriteLikeDriverHowMuchSits && isUserWriteLikeDriverAuto) {
            String mes = "Ваш автомобиль: ";
            sendMessage(chatId, mes);
            sendMessage(chatId, text);
            TripActive trip = getTripActive(tripId);
            trip.setAuto(text);
            tripActiveRepository.save(trip);
            isUserWriteLikeDriverHowMuchSits = true;
            sendMessage(chatId, "Введите количество свободных мест (не более 8): ");
        }
        else if (isUserWriteLikeDriverTo && isUserWriteLikeDriverWhen && !isUserWriteLikeDriverPrice &&
                isUserWriteLikeDriverHowMuchSits && isUserWriteLikeDriverAuto) {
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
                isUserWriteLikeDriverHowMuchSits && isUserWriteLikeDriverAuto) {
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
            isUserWriteLikeDriverTo = false;
            isUserWriteLikeDriverWhen = false;
            isUserWriteLikeDriver = false;
            isUserWriteLikeDriverPrice = false;
            isUserWriteLikeDriverHowMuchSits = false;
            isUserWriteLikeDriverAuto = false;
            sendMessage(chatId, "Ваша поездка спланирована, информацию о ней вы можете найти " +
                    "в меню, в разделе /history.");
        }
    }

    private void passengerLogicImplementation(Update update) {
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        if (!isUserWriteLikePassengerTo && !isUserWriteLikePassengerWhen) {
            Location location = update.getMessage().getLocation();
            String address = createLocationFromCoordinates(chatId, location);
            String mes = "Ищем поездку из: " + address;
            sendMessage(chatId, mes);
            ActiveTripQuestions trip = new ActiveTripQuestions();
            trip.setTripId(chatId);
            trip.setLongFrom(location.getLongitude());
            trip.setLatFrom(location.getLatitude());
            trip.setCityFrom(address);
            trip.setCityTo("some");
            trip.setDateFormat("27.04.2099/15:00:00");
            tripRepository.save(trip);
            isUserWriteLikePassengerTo = true;
            sendMessage(chatId, "Введите куда вы поедете:");
        } else if (!isUserWriteLikePassengerWhen && isUserWriteLikePassengerTo) {
            Location location = update.getMessage().getLocation();
            String address = createLocationFromCoordinates(chatId, location);
            String mes = "Ищем поездку в: " + address;
            sendMessage(chatId, mes);
            Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
            for (ActiveTripQuestions trip : trips) {
                if (trip.getTripId().equals(chatId)) {
                    trip.setCityTo(address);
                    trip.setLongTo(location.getLongitude());
                    trip.setLatTo(location.getLatitude());
                    tripRepository.save(trip);
                }
            }
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
                Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
                for (ActiveTripQuestions trip : trips) {
                    if (trip.getTripId().equals(chatId)) {
                        String date = text;
                        trip.setDateFormat(date);
                        tripRepository.save(trip);
                        isUserWriteLikePassengerTo = false;
                        isUserWriteLikePassengerWhen = false;
                        isUserWriteLikePassenger = false;
                    }
                }
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
    private void getPassengerHistory(long chatId) {
        Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
        String answer = "Ваши заявки на поиск поездки:\n";
        sendMessage(chatId, answer);
        for (ActiveTripQuestions trip : trips) {
            if (trip.getTripId().equals(chatId)) {
                StringBuilder str = new StringBuilder("Дата: " + trip.getDateFormat() + ";\n");
                str.append("Откуда: " + trip.getCityFrom() + ";\n");
                str.append("Куда: " + trip.getCityTo() + ".\n");
                String messgae = String.valueOf(str);
                sendMessage(chatId, messgae);
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
                StringBuilder str = new StringBuilder("Дата: " + trip.getTripDate() + ";\n");
                str.append("Откуда: " + trip.getCityFrom() + ";\n");
                str.append("Куда: " + trip.getCityTo() + ".\n");
                str.append("Автомобиль: " + trip.getAuto() + ".\n");
                str.append("Количество мест: " + trip.getCountOfSits() + ".\n");
                str.append("Стоимость за место: " + trip.getTripPrice() + ".\n");
                String messgae = String.valueOf(str);
                sendMessage(chatId, messgae);
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

    // Этот метод будет при формировании поездки водителем придавать id маршрута из файла ways.txt
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
}
