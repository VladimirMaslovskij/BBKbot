package io.proj3ct.TestBlaBlaBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.proj3ct.TestBlaBlaBot.config.BotConfig;
import io.proj3ct.TestBlaBlaBot.config.MyGeocoder;
import io.proj3ct.TestBlaBlaBot.model.*;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.triangulate.tri.Tri;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
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

@EnableScheduling
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
    static final String ALL_TRIPS = "allTrips";
    static final String TO_BOOK = "toBook";
    static final String SITS = "sits";
    static final String TRIP_WAS = "tripWas";
    static final String TRIP_NOT_WAS = "tripNotWas";
    static final String SKIP_ASK = "skipAsk";
    static final String RATE_DRIVER = "rateDriver";
    static final String DELETE_TRIP = "deleteTrip";
    static final String DELETE_QUESTION = "deleteQuestion";
    static final String DELETE_BOOK = "deleteBook";
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
    private boolean isShowing = false;
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
        listOfCommands.add(new BotCommand("/find", "Доступные поездки"));
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

    private void driverSkipComment(Update update, Integer messageId) {
        if (update.getCallbackQuery().getData().equals(NO_COMMENT)) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            String info = "Ваша поездка спланирована, информацию о ней вы можете найти " +
                    "в меню, в разделе /history.";
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
            executeEditMessageText(info, chatId, messageId);
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
            driverSkipComment(update, update.getCallbackQuery().getMessage().getMessageId());
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
                sendMessage(chatId, "Введите куда вы хотите поехать:");
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
                    String mes = "Ваша заявка на поездку сформирована и спланирована на:";
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
                    String finalMes = "Вы можете посмотреть информацию о вашей заявке в разделе /history, " +
                            "чтобы посмотреть все активные спланированные поездки, нажмите \"Поездки\"";
                    SendMessage sendMessage = new SendMessage();
                    sendMessage.setText(finalMes);
                    sendMessage.setChatId(chatId);
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                    List<InlineKeyboardButton> upRow = new ArrayList<>();
                    InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                            .callbackData(ALL_TRIPS)
                            .text("Поездки")
                            .build();
                    upRow.add(inlineKeyboardButtonNoComment);
                    rowsLine.add(upRow);
                    markup.setKeyboard(rowsLine);
                    sendMessage.setReplyMarkup(markup);
                    executeMessage(sendMessage);
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
                SendMessage sendMessage = new SendMessage();
                sendMessage.setText(trip.getTripInfo());
                sendMessage.setChatId(chatId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(DELETE_QUESTION + "/" + trip.getTripId())
                        .text("Удалить")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                executeMessage(sendMessage);
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
                SendMessage sendMessage = new SendMessage();
                StringBuilder str = new StringBuilder(trip.getTripInfo());
                if (trip.tripHasPassengers()) {
                    String finalMes = "Ваши пассажиры: ";
                    str.append(finalMes);
                    List<String> passengersNames = trip.getPassengersNames();
                    for (int i = 0; i < passengersNames.size(); i++) {
                        str.append("@" + passengersNames.get(i));
                    }
                }
                sendMessage.setChatId(chatId);
                sendMessage.setText(String.valueOf(str));
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                    InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                            .callbackData(DELETE_TRIP + "/" + trip.getTripId())
                            .text("Удалить")
                            .build();
                    upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                executeMessage(sendMessage);
            }
        }
        log.info("Request a history of trips");
    }
    private void getBookingTrips(long chatId) {
        User user = getUserByChatId(chatId);
        List<Long> ids = user.getMyBookingTrips();
        Iterable<TripActive> trips = tripActiveRepository.findAllById(ids);
        String answer = "Ваши забронированные поездки:\n";
        sendMessage(chatId, answer);
        for (TripActive trip : trips) {
            if (trip.getDriver().equals(chatId)) {
                SendMessage sendMessage = new SendMessage();
                StringBuilder str = new StringBuilder(trip.getTripInfo());
                    String finalMes = "Водитель: ";
                    str.append(finalMes);
                    String driver = "@" + getUserByChatId(trip.getDriverId()).getUserName();
                    str.append(driver);
                sendMessage.setChatId(chatId);
                sendMessage.setText(String.valueOf(str));
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(DELETE_BOOK + "/" + trip.getTripId())
                        .text("Отменить бронь")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                executeMessage(sendMessage);
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
            User user = getUserByChatId(1313359155L);
            user.setMyBookingTrips("");
            user.setCounterBookingTrips();
            userRepository.save(user);
            tripActiveRepository.deleteById(13133591551L);
            tripActiveRepository.deleteById(13133591552L);
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
                    getBookingTrips(chatId);
                    break;
                case "/help":
                    sendMessage(chatId, HELP_TEXT);
                    log.info("Request info");
                    break;
                case "/find":
                    if (isUserHasTripQuestion(chatId)) {
                        showAllTrips(chatId);
                        isShowing = true;
                    }
                    else
                        sendMessage(chatId, "Чтобы просматривать доступные поездки необходимо " +
                                "создать заявку на поездку, для этого нажмите /start и выбирите \"Попутчик\"");
                    break;
                default:
                    sendMessage(chatId, "Извините, такой команды не существует.");
            }
        }
    }

    private boolean isUserHasTripQuestion(Long chatId) {
//        String chatIdStr = String.valueOf(chatId * 10);
        String chatIdStr = String.valueOf(chatId);
        boolean result = false;
        for (int i = 1; i < 5; i++) {
            Long id = Long.valueOf(chatIdStr + i);
            if (tripRepository.existsById(id))
                result = true;
        }
        return result;
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
            if ((checkQuestionId(chatId) != null)) {
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
        } else if (callbackData.equals(ALL_TRIPS)) {
            showAllTrips(chatId, messageId);
            isShowing = true;
        }
        else if (callbackData.equals(SHOW_ACTIVE_TRIPS) && (checkAdmin(chatId))) {
            showAllTrips(chatId, messageId);
        } else if (callbackData.startsWith(TO_BOOK) && isShowing) {
            Long tripId = Long.valueOf(callbackData.split("/")[1]);
            TripActive trip = getTripActive(tripId);
            int countOfSits = trip.getCountOfSits();
            isShowing = false;
            setCountOfSits(chatId, countOfSits, tripId);
        } else if (callbackData.startsWith(SITS)) {
            int countOfSits = Integer.valueOf(callbackData.split("/")[1]);
            Long tripId = Long.valueOf(callbackData.split("/")[2]);
            toBookTrip(chatId, tripId, countOfSits, messageId);
        } else if (callbackData.startsWith(DELETE_TRIP)) {
            Long idDeletedTrip = Long.valueOf(callbackData.split("/")[1]);
            deleteTripFromUser(chatId, idDeletedTrip, messageId);
        } else if (callbackData.startsWith(DELETE_BOOK)) {
            Long idDeletedBook = Long.valueOf(callbackData.split("/")[1]);
            deleteBookFromUser(chatId, idDeletedBook, messageId);
        }
        else if (callbackData.startsWith(DELETE_QUESTION)) {
            Long idDeletedTrip = Long.valueOf(callbackData.split("/")[1]);
            deleteQuestionFromUser(chatId, idDeletedTrip, messageId);
        } else if (callbackData.equals(SKIP_ASK)) {
            executeEditMessageText("Спасибо за использование нашего бота, надеюсь мы еще не раз поможем" +
                    " Вам добраться куда вы захотите", chatId, messageId);
        } else if (callbackData.startsWith(TRIP_WAS)) {
            Long driverId = Long.valueOf(callbackData.split("/")[1]);
            estimationDriver(chatId, driverId, messageId);
        }
        else if (callbackData.startsWith(TRIP_NOT_WAS)) {
            Long driverId = Long.valueOf(callbackData.split("/")[1]);
            askAboutReason(chatId, driverId, messageId);
        }
        else if (callbackData.startsWith(RATE_DRIVER)) {
            int driverRate = Integer.valueOf(callbackData.split("/")[1]);
            Long driverId = Long.valueOf(callbackData.split("/")[2]);
            User user = getUserByChatId(driverId);
            user.setRating(driverRate);
            userRepository.save(user);
            executeEditMessageText("Спасибо за использование нашего бота, надеюсь мы еще не раз поможем" +
                    "Вам добраться куда вы захотите", chatId, messageId);
        }
        else if (callbackData.equals(START_TRIP) && (checkAdmin(chatId))) {
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

    private void deleteBookFromUser(long chatId, Long idDeletedBook, long messageId) {
        TripActive tripActive = getTripActive(idDeletedBook);
        User user = getUserByChatId(chatId);
        String text = "Бронирование отменено.";
        int countOfSits = 0;
        System.out.println(String.valueOf(chatId) +"   " + String.valueOf(idDeletedBook));
        tripActive.deletePassengerName(user.getUserName());
        List<String> passangers = tripActive.getPassengers();
        System.out.println(passangers);
        StringBuilder newPassengers = new StringBuilder();
        for (String pasInfo: passangers) {
            if (pasInfo.startsWith(String.valueOf(chatId))) {
                countOfSits = Integer.parseInt(pasInfo.split("_")[1]);
            }
            else newPassengers.append(pasInfo);
        }
        user.deleteMyBookingTrip(String.valueOf(tripActive.getTripId()));
        tripActive.setPassenger(String.valueOf(newPassengers));
        tripActive.setCountOfSits(tripActive.getCountOfSits() + countOfSits);
        tripActiveRepository.save(tripActive);
        userRepository.save(user);
        executeEditMessageText(text, chatId, messageId);
    }

    private void askAboutReason(long chatId, Long driverId, long messageId) {
        EditMessageText messageText = new EditMessageText();
        messageText.setText("Если поездка не состоялась по причине того, что водитель:\n" +
                "- не вышел на связь;\n" +
                "- передумал в последний момент и не оповестил Вас;\n" +
                "- уехал без вас;\n" +
                "- отказал Вам без причины,\n" +
                "то нажмите \"Из-за водителя\"\n" +
                "Если поездка не состоялась по другой причине, нажмите \"Из-за меня\" или \"Пропустить\"");
        messageText.setChatId(chatId);
        messageText.setMessageId((int) messageId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> upRow = new ArrayList<>();
        List<InlineKeyboardButton> downRow = new ArrayList<>();
            InlineKeyboardButton driverBad = InlineKeyboardButton.builder()
                    .callbackData(RATE_DRIVER + "/" + 1 + "/" + driverId)
                    .text("Из-за водителя")
                    .build();
            upRow.add(driverBad);
        InlineKeyboardButton meBad = InlineKeyboardButton.builder()
                .callbackData(SKIP_ASK)
                .text("Из-за меня")
                .build();
        upRow.add(meBad);
        InlineKeyboardButton skip = InlineKeyboardButton.builder()
                .callbackData(SKIP_ASK)
                .text("Пропустить")
                .build();
        rowsLine.add(upRow);
        downRow.add(skip);
        rowsLine.add(downRow);
        markup.setKeyboard(rowsLine);
        messageText.setReplyMarkup(markup);
        try {
            execute(messageText);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void estimationDriver(long chatId, Long driverId, long messageId) {
        EditMessageText messageText = new EditMessageText();
        messageText.setText("Пожалуйста, оцените водителя и саму поездку по 5-бальной шкале: ");
        messageText.setChatId(chatId);
        messageText.setMessageId((int) messageId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> upRow = new ArrayList<>();
        List<InlineKeyboardButton> downRow = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                    .callbackData(RATE_DRIVER + "/" + i + "/" + driverId)
                    .text(String.valueOf(i))
                    .build();
            upRow.add(inlineKeyboardButtonNoComment);
        }
        InlineKeyboardButton skip = InlineKeyboardButton.builder()
                .callbackData(SKIP_ASK)
                .text("Пропустить")
                .build();
        rowsLine.add(upRow);
        downRow.add(skip);
        rowsLine.add(downRow);
        markup.setKeyboard(rowsLine);
        messageText.setReplyMarkup(markup);
        try {
            execute(messageText);
        } catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void deleteTripFromUser(long chatId, Long idDeletedTrip, long messageId) {
        tripActiveRepository.deleteById(idDeletedTrip);
        String text ="Поездка удалена";
        executeEditMessageText(text, chatId, messageId);
    }
    private void deleteQuestionFromUser(long chatId, Long idDeletedTrip, long messageId) {
        tripRepository.deleteById(idDeletedTrip);
        String text ="Поездка удалена";
        executeEditMessageText(text, chatId, messageId);
    }

    private void toBookTrip(long chatId, Long tripId, int countOfSits, long messageId) {
        TripActive trip = getTripActive(tripId);
        trip.addPassenger(String.valueOf(chatId), countOfSits);
        User passenger = getUserByChatId(chatId);
        passenger.addMyBookingTrip(tripId);
        trip.addPassengerName(passenger.getUserName());
        trip.setCountOfSits(trip.getCountOfSits() - countOfSits);
        String driverName = findUserNameById(trip.getDriverId());
        tripActiveRepository.save(trip);
        userRepository.save(passenger);
        String text ="Поездка забронирована. Чтобы посмотреть детали поездки, зайдите в раздел " +
                "/history, что бы связаться с водителем - напишите ему: @" + driverName;
        executeEditMessageText(text, chatId, messageId);
        sendMessage(trip.getDriverId(), "Мы нашли пассажира к вашей поездке!\n" +
                "Можете прямо сейчас написать ему для уточнения деталей: @" + passenger.getUserName() + "" +
                ", либо позже он сам напишет вам.");
    }

    private User getUserByChatId(long chatId) {
        Optional<User> users = userRepository.findById(chatId);
        if (users.isPresent()) {
            User user = users.get();
            return user;
        }
        return null;
    }

    private void setCountOfSits(Long chatId, int countOfSits, Long tripId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setText("Выберите количество мест: ");
        sendMessage.setChatId(chatId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> upRow = new ArrayList<>();
        for (int i = 1; i <= countOfSits; i++) {
            InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                    .callbackData(SITS + "/" + i + "/" + tripId)
                    .text(String.valueOf(i))
                    .build();
            upRow.add(inlineKeyboardButtonNoComment);
        }
        rowsLine.add(upRow);
        markup.setKeyboard(rowsLine);
        sendMessage.setReplyMarkup(markup);
        executeMessage(sendMessage);
    }

    private void showAllTrips(Long chatId, long messageId) {
        String finalMes = "Чтобы забронировать поездку нажмите кнопку \"Забронировать\" " +
                "и выберите количество необходимых мест.";
        sendMessage(chatId, finalMes);
        var trips = tripActiveRepository.findAll();
        for (TripActive trip : trips) {
            if ((trip.isActive()) && (trip.getCountOfSits() > 0)
                //&& !(trip.getPassengers().contains(chatId)) && (trip.getDriverId() != chatId)
            ) {
                EditMessageText sendMessage = new EditMessageText();
                sendMessage.setText(trip.getTripInfo());
                sendMessage.setChatId(chatId);
                sendMessage.setMessageId((int) messageId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(TO_BOOK + "/" + trip.getTripId())
                        .text("Забронировать")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                try {
                    execute(sendMessage);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
    private void showAllTrips(Long chatId) {
        String finalMes = "Чтобы забронировать поездку нажмите кнопку \"Забронировать\" " +
                "и выберите количество необходимых мест.";
        sendMessage(chatId, finalMes);
        var trips = tripActiveRepository.findAll();
        for (TripActive trip : trips) {
            if ((trip.isActive()) && (trip.getCountOfSits() > 0)
            //&& !(trip.getPassengers().contains(chatId)) && (trip.getDriverId() != chatId)
            ) {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setText(trip.getTripInfo());
                sendMessage.setChatId(chatId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(TO_BOOK + "/" + trip.getTripId())
                        .text("Забронировать")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                executeMessage(sendMessage);
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
            if (trip.isActive()) {
                counter++;
                ids.remove(trip.getTripId());
            }
        }
        if (((result + counter > result) && (result + counter <= chatId)) || (counter == 1L))
            return ids.get(0);
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
    @Scheduled(cron = "${interval-in-cron}")
    private void checkCompletedTrips() throws ParseException {
//        askAboutTrip();
        deleteActiveTrips();
        remindAboutTrips();
        sendMessage(1313359155, "Завершенные поездки или поездки с истекшим временем были удалены.");
    }

    private void askAboutTrip() throws ParseException {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy/HH:mm");
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        for (TripActive trip : tripsActive) {
            Date newDate = dateFormat.parse(trip.getTripDate());
            if (newDate.getTime() < date.getTime()) {
                List<String> passengersId = trip.getPassengers();
                for (int i = 0; i < passengersId.size(); i++) {
                    User user = getUserByChatId(Long.parseLong(passengersId.get(i).split("_")[0]));
                    SendMessage message = new SendMessage();
                    message.setChatId(user.getCharId());
                    message.setText("У Вас вчера была запланирована поездка из " + trip.getCityFrom() +
                            ", в " + trip.getCityTo() + ", подскажите пожалуйста состоялась" +
                            " ли она? Ваш ответ поможет улучшить работу сервиса и определить порядочность водителя.");
                    InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                    List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                    List<InlineKeyboardButton> upRow = new ArrayList<>();
                    List<InlineKeyboardButton> downRow = new ArrayList<>();
                    InlineKeyboardButton tripWas = InlineKeyboardButton.builder()
                            .callbackData(TRIP_WAS + "/" + trip.getDriverId())
                            .text("Состоялась")
                            .build();
                    InlineKeyboardButton tripNotWas = InlineKeyboardButton.builder()
                            .callbackData(TRIP_NOT_WAS + "/" + trip.getDriverId())
                            .text("Не состоялась")
                            .build();
                    InlineKeyboardButton skipAsk = InlineKeyboardButton.builder()
                            .callbackData(SKIP_ASK)
                            .text("Пропустить")
                            .build();
                    upRow.add(tripWas);
                    upRow.add(tripNotWas);
                    rowsLine.add(upRow);
                    downRow.add(skipAsk);
                    rowsLine.add(downRow);
                    markup.setKeyboard(rowsLine);
                    message.setReplyMarkup(markup);
                    executeMessage(message);
                }
            }
        }
    }
    private void deleteActiveTrips() throws ParseException {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        Date newDate;
        System.out.println("Here");
        Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
        List<Long> deletedIds = new ArrayList<>();
        for (ActiveTripQuestions trip : trips) {
            System.out.println("Here1");
            newDate = dateFormat.parse(trip.getDateFormat());
            if (newDate.getTime() < date.getTime()) {
                System.out.println("Here2");
                deletedIds.add(trip.getTripId());
            }
        }
        for (Long id: deletedIds) {
            tripRepository.deleteById(id);
        }
        dateFormat = new SimpleDateFormat("dd.MM.yyyy/HH:mm");
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        List<Long> deletedIdsTrips = new ArrayList<>();
        for (TripActive trip : tripsActive) {
            newDate = dateFormat.parse(trip.getTripDate());
            if (newDate.getTime() < date.getTime()) {
                List<String> passengersId = trip.getPassengers();
                for (int i = 0; i < passengersId.size(); i++) {
                    User user = getUserByChatId(Long.parseLong(passengersId.get(i).split("_")[0]));
                    user.deleteMyBookingTrip(String.valueOf(trip.getTripId()));
                    userRepository.save(user);
                }
                deletedIdsTrips.add(trip.getTripId());
            }
        }
        for (Long id: deletedIdsTrips) {
            tripActiveRepository.deleteById(id);
        }
    }
    private void remindAboutTrips() throws ParseException {
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy/HH:mm");
        Date newDate;
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        for (TripActive trip : tripsActive) {
            newDate = dateFormat.parse(trip.getTripDate());
            if ((newDate.getTime() > date.getTime()) && (newDate.getTime() < date.getTime() + 86400000L)) {
                List<String> passengersId = trip.getPassengers();
                for (int i = 0; i < passengersId.size(); i++) {
                    User user = getUserByChatId(Long.parseLong(passengersId.get(i).split("_")[0]));
                    String time = trip.getTripDate().split("/")[1];
                    String driverName = findUserNameById(trip.getDriver());
                    sendMessage(user.getCharId(), "Напоминаю Вам о спланированной сегодня поездке в"
                    + time +", для связи можете написать водителю: @" + driverName);
                    sendMessage(trip.getTripId(), "Напоминаю Вам о спланированной сегодня поездке в"
                            + time);
                }
            }
        }
    }
}
