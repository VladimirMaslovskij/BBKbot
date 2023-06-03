package io.proj3ct.TestBlaBlaBot.service;

import com.vdurmont.emoji.EmojiParser;
import io.proj3ct.TestBlaBlaBot.config.BotConfig;
import io.proj3ct.TestBlaBlaBot.config.MyGeocoder;
import io.proj3ct.TestBlaBlaBot.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Location;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.io.IOException;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

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
    static final String SHOW_SUITABLE_TRIPS = "showSuitableTrips";
    static final String WHITE_USER = "addUserToWhiteList";
    static final String UNWHITE_USER = "removeUserFromWhiteList";
    static final String UNBAN_USER = "unbanUser";
    static final String BAN_USER = "banUser";
    static final String STOP_WORK = "stopWork";
    static final String STOP_WORK_PASSENGER = "stopWorkPassenger";
    static final String MONTH_NUMBER = "monthNumber";
    static final String DAY_NUMBER = "dayNumber";
    private static HashMap<Integer, String> months = new HashMap<>() {{
        put(0, "Январь");
        put(1, "Февраль");
        put(2, "Март");
        put(3, "Апрель");
        put(4, "Май");
        put(5, "Июнь");
        put(6, "Июль");
        put(7, "Август");
        put(8, "Сентябрь");
        put(9, "Октябрь");
        put(10, "Ноябрь");
        put(11, "Декабрь");
    }};
    //    private boolean isUserWriteLikePassenger = false;
    //    private boolean isUserWriteLikePassengerTo = false;
    //    private boolean isUserWriteLikePassengerWhen = false;
    //    private boolean isUserWriteLikeDriver = false;
    //    private boolean isUserWriteLikeDriverTo = false;
    //    private boolean isUserWriteLikeDriverWhen = false;
    //    private boolean isUserWriteLikeDriverPrice = false;
    //    private boolean isUserWriteLikeDriverHowMuchSits = false;
    //    private boolean isUserWriteLikeDriverAuto = false;
    //    private boolean isUserWriteLikeDriverComment = false;
    //    private boolean isShowing = false;
    Map<Long, UserState> userStateMap = new HashMap<>();
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActiveTripQuestionsRepository tripRepository;
    @Autowired
    private TripActiveRepository tripActiveRepository;
    @Autowired
    private UserStateRepository userStateRepository;


    static final String HELP_TEXT = "Данный бот используется для поиска попутчиков водителями, и наоборот.\n" +
            "     Для водителя необходимо:\n" +
            "1. В меню бота выбрать новую поездку (/start). Далее - вариант \"Водитель\" \n" +
            "2. Указать место отправления, место прибытия," +
            " количество пассажиров, цену поездки для одного человека, а также дату и время планируемой поездки.\n" +
            "3. Если есть пассажиры, которым Ваша поездка подходит, им автоматически будет разослано" +
            " оповещение о Вашей поездке. После - Ваша поездка будет добавлена в базу поездок.\n" +
            "4. Ожидать ответа от бота, когда найдутся ваши попутчики. \n" +
            "     Для пассажира необходимо:\n" +
            "1. В меню бота выбрать новую поездку (/start). Далее - вариант \"Пассажир\" \n" +
            "2. Выбрать место отправления, место прибытия, а также дату.\n" +
            "3. Ожидать ответа от бота с вариантами возможных поездок.\n";
    static final String INFO_PHOTO_TEXT = EmojiParser.parseToUnicode("Для выбора точки " +
            "нажмите на вложения" + ":paperclip:" + " (1), далее на кнопку выбора " +
            "гелокации (2) и выберите точку на карте или введите адрес, нажав на иконку " + ":mag:" + " лупы.");

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
        UserState thisState = checkUserState(update);
            if (thisState.isUserWriteLikeDriver()) {
                driverLogicImplementation(update, thisState);
            } else if (thisState.isUserWriteLikePassenger()) {
                passengerLogicImplementation(update, thisState);
            } else {
                if (update.hasMessage() && update.getMessage().hasText()) {
                    if ((update.getMessage().getText().equals("/start"))
                        || (update.getMessage().getText().equals("/find"))
                            || (update.getMessage().getText().equals("/history"))
                            || (update.getMessage().getText().equals("/help"))
                            || (update.getMessage().getText().contains("/send"))
                            || (update.getMessage().getText().contains("/ban"))
                            || (update.getMessage().getText().contains("/prem"))
                            || (update.getMessage().getText().contains("/reset"))
                            || (update.getMessage().getText().contains("/unban"))
                            || (update.getMessage().getText().contains("/unprem"))) {
                        if (userRepository.existsById(update.getMessage().getChatId())) {
                            if (checkUserStatus(update.getMessage().getChatId()) &&
                                    !getUserByChatId(update.getMessage().getChatId()).isBan()) {
                                botLogicIfUpdateIsText(update, thisState);
                            } else if (!checkUserStatus(update.getMessage().getChatId()) &&
                                    !getUserByChatId(update.getMessage().getChatId()).isBan()) {
                                sendMessage(update.getMessage().getChatId(), "Для использования бота " +
                                        "подпишитесь на наш канал: @bla_bla_krym_channel");
                            } else if (!checkUserStatus(update.getMessage().getChatId()) &&
                                    getUserByChatId(update.getMessage().getChatId()).isBan()) {
                                sendMessage(update.getMessage().getChatId(), "Ваш аккаунт забанен. " +
                                        "Для использования бота обратитесь в администрацию канала @bla_bla_krym_channel");
                            }
                        } else {
                            registerUser(update.getMessage());
                        }
                    }
                } else if (update.hasCallbackQuery()) {
                    botLogicIfUpdateIsCallback(update, thisState);
                }
            }
    }

    private UserState checkUserState(Update update) {
        Long chatId = 0L;
        if (update.hasCallbackQuery())
            chatId = update.getCallbackQuery().getMessage().getChatId();
        else if (update.hasMessage() && update.getMessage().hasText())
            chatId = update.getMessage().getChatId();
        else chatId = update.getMessage().getChatId();
        UserState userState = new UserState();
        if (userStateRepository.findById(chatId).isEmpty()) {
            userState.setChatId(chatId);
            userStateRepository.save(userState);
        } else {
            Optional<UserState> optState = userStateRepository.findById(chatId);
            if (optState.isPresent())
                userState = optState.get();
        }
        return userState;
    }

    private void driverSkipComment(Update update, Integer messageId, UserState thisState) {
        if (update.getCallbackQuery().getData().equals(NO_COMMENT)) {
            Long chatId = update.getCallbackQuery().getMessage().getChatId();
            String info = "Ваша поездка спланирована, информацию о ней вы можете найти " +
                    "в меню, в разделе /history.";
            Long tripId = checkTripId(update.getCallbackQuery().getMessage().getChatId());
            TripActive tripActive = getTripActive(tripId);
            List<ActiveTripQuestions> questions = checkQuestionToSuitable(tripActive, tripActive.getCityFrom(),
                    tripActive.getCityTo());
            for (ActiveTripQuestions quest: questions) {
                sendMessage(quest.getPassengerId(), "Здравствуйте! Возможно, мы нашли " +
                        "подходящую Вам поездку, посмотрите её! Чтобы забронировать поездку, нажмите" +
                        "\"Забронировать\". Если вы не хотите получать уведомления о подходящих " +
                        "поездках - удалите свою заявку в разделе /history.");
                SendMessage message = sendTripInfo(quest.getPassengerId(), tripActive);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(TO_BOOK + "/" + tripActive.getTripId())
                        .text("Забронировать")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                message.setReplyMarkup(markup);
                executeMessage(message);
            }
            tripActive.setActive(true);
            tripActive.setComment(null);
            tripActiveRepository.save(tripActive);
            thisState.setUserWriteLikeDriverTo(false);
            thisState.setUserWriteLikeDriverWhen(false);
            thisState.setUserWriteLikeDriver(false);
            thisState.setUserWriteLikeDriverPrice(false);
            thisState.setUserWriteLikeDriverHowMuchSits(false);
            thisState.setUserWriteLikeDriverAuto(false);
            thisState.setUserWriteLikeDriverComment(false);
            userStateRepository.save(thisState);
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
                activeQuestionTripCount() + " активных запросов на поездки.";
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
                .text("Активные запросы")
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

    private void driverLogicImplementation(Update update, UserState thisState) {
        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getData().startsWith(NO_COMMENT)) {
                driverSkipComment(update, update.getCallbackQuery().getMessage().getMessageId(), thisState);
            } else if (update.getCallbackQuery().getData().startsWith(STOP_WORK)) {
                Long tripId = Long.valueOf(update.getCallbackQuery().getData().split("/")[1]);
                stopActiveTrip(thisState, tripId, update.getCallbackQuery().getMessage().getMessageId());
            } else if (update.getCallbackQuery().getData().startsWith(MONTH_NUMBER)) {
                String monthOne = update.getCallbackQuery().getData().split("/")[1];
                String month = monthOne + ".";
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                long messageId = update.getCallbackQuery().getMessage().getMessageId();
                Long tripId = checkTripId(chatId);
                TripActive tripActive = getTripActive(tripId);
                tripActive.setTripDate(month);
                tripActiveRepository.save(tripActive);
                showDays(chatId, tripId, messageId, monthOne);
            } else if (update.getCallbackQuery().getData().startsWith(DAY_NUMBER)) {
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                long messageId = update.getCallbackQuery().getMessage().getMessageId();
                Long tripId = checkTripId(chatId);
                TripActive tripActive = getTripActive(tripId);
                StringBuilder stringBuilder = new StringBuilder(tripActive.getTripDate());
                stringBuilder.append(update.getCallbackQuery().getData().split("/")[1] + "/");
                String date = String.valueOf(stringBuilder);
                tripActive.setTripDate(date);
                tripActiveRepository.save(tripActive);
                askDriverWithDelete(chatId, "Введите время поездки в формате " +
                        "05:00.", tripId, messageId);
                thisState.setUserWriteLikeDriverWhen(true);
                userStateRepository.save(thisState);
            }
        }
        else {
            var chatId = update.getMessage().getChatId();
            var text = update.getMessage().getText();
            var tripId = checkTripId(chatId);
            if (!thisState.isUserWriteLikeDriverTo() && !thisState.isUserWriteLikeDriverWhen()
                    && !thisState.isUserWriteLikeDriverPrice() &&
                    !thisState.isUserWriteLikeDriverHowMuchSits() &&
                    !thisState.isUserWriteLikeDriverAuto() && !thisState.isUserWriteLikeDriverComment()) {
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
                thisState.setUserWriteLikeDriverTo(true);
                tripActiveRepository.save(trip);
                userStateRepository.save(thisState);
                sendInfoPhoto(chatId);
                askDriver(chatId, INFO_PHOTO_TEXT, tripId);
            } else if (!thisState.isUserWriteLikeDriverWhen() &&
                    thisState.isUserWriteLikeDriverTo() &&
                    !thisState.isUserWriteLikeDriverPrice() &&
                    !thisState.isUserWriteLikeDriverHowMuchSits() &&
                    !thisState.isUserWriteLikeDriverAuto() && !thisState.isUserWriteLikeDriverComment()) {
                Location location = update.getMessage().getLocation();
                String address = createLocationFromCoordinates(chatId, location);
                String mes = "Вы едете в : " + address;
                sendMessage(chatId, mes);
                TripActive trip = getTripActive(tripId);
                trip.setLongTo(location.getLongitude());
                trip.setLatTo(location.getLatitude());
                trip.setCityTo(address);
                tripActiveRepository.save(trip);
                showMonths(chatId, tripId);
            }
            else if (thisState.isUserWriteLikeDriverTo() &&
                    thisState.isUserWriteLikeDriverWhen() &&
                    !thisState.isUserWriteLikeDriverPrice() &&
                    !thisState.isUserWriteLikeDriverHowMuchSits() &&
                    !thisState.isUserWriteLikeDriverAuto() &&
                    !thisState.isUserWriteLikeDriverComment()) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("kk:mm");
                try {
                    System.out.println(dateFormat.parse(text));
                } catch (ParseException e) {
                    askDriver(chatId, "Введен неверный формат даты, попробуйте еще раз.", tripId);
                    throw new RuntimeException(e);
                }
                TripActive trip = getTripActive(tripId);
                StringBuilder str = new StringBuilder(trip.getTripDate());
                str.append(text);
                String finalDate = String.valueOf(str);
                trip.setTripDate(finalDate);
                tripActiveRepository.save(trip);
                String month = trip.getTripDate().split("\\.")[0];
                String thisMonth = months.get(Integer.parseInt(month));
                String secondSide = trip.getTripDate().split("\\.")[1];
                String day = secondSide.split("/")[0];
                String time = secondSide.split("/")[1];
                StringBuilder stringBuilder = new StringBuilder(EmojiParser.parseToUnicode("Выбрана дата и" +
                        " время поездки: \n" +
                        "Месяц" + ":date:" +": " + thisMonth + ", " +
                        "День: " + day +"\n" +
                        "Время" + ":stopwatch:" + ": " + time + "\n"));
                sendMessage(chatId, String.valueOf(stringBuilder));
                    thisState.setUserWriteLikeDriverAuto(true);
                    userStateRepository.save(thisState);
                    askDriver(chatId, "Введите пожалуйста название Вашего автомобиля", tripId);
            } else if (thisState.isUserWriteLikeDriverTo() &&
                    thisState.isUserWriteLikeDriverWhen() &&
                    !thisState.isUserWriteLikeDriverPrice() &&
                    !thisState.isUserWriteLikeDriverHowMuchSits() &&
                    !thisState.isUserWriteLikeDriverComment() && thisState.isUserWriteLikeDriverAuto()) {
                String mes = "Ваш автомобиль: ";
                sendMessage(chatId, mes);
                sendMessage(chatId, text);
                TripActive trip = getTripActive(tripId);
                trip.setAuto(text);
                tripActiveRepository.save(trip);
                thisState.setUserWriteLikeDriverHowMuchSits(true);
                userStateRepository.save(thisState);
                askDriver(chatId, "Введите количество свободных мест (не более 8): ", tripId);
            } else if (thisState.isUserWriteLikeDriverTo() &&
                    thisState.isUserWriteLikeDriverWhen() &&
                    !thisState.isUserWriteLikeDriverPrice() &&
                    thisState.isUserWriteLikeDriverHowMuchSits() &&
                    thisState.isUserWriteLikeDriverAuto() && !thisState.isUserWriteLikeDriverComment()) {
                if ((Integer.parseInt(text) < 1)
                        || (Integer.parseInt(text) > 8)) {
                    askDriver(chatId, "Количество возможных мест может быть не менее 1 и не более 8" +
                            "Пожалуйста, введите количество возможных мест еще раз.", tripId);
                }
                String mes = "Выбрано следующее количество свободных мест: " + text;
                sendMessage(chatId, mes);
                TripActive trip = getTripActive(tripId);
                trip.setCountOfSits(Integer.parseInt(text));
                tripActiveRepository.save(trip);
                askDriver(chatId, "Введите стоимость проезда для одного человека (в рублях): ", tripId);
                thisState.setUserWriteLikeDriverPrice(true);
                userStateRepository.save(thisState);
            } else if (thisState.isUserWriteLikeDriverTo() &&
                    thisState.isUserWriteLikeDriverWhen() &&
                    thisState.isUserWriteLikeDriverPrice() &&
                    thisState.isUserWriteLikeDriverHowMuchSits() &&
                    thisState.isUserWriteLikeDriverAuto() &&
                    !thisState.isUserWriteLikeDriverComment()) {
                if ((Integer.parseInt(text) < 1)
                        || (Integer.parseInt(text) > 100000)) {
                    askDriver(chatId, "Стоимость поездки не может быть менее 0 рублей и более 100 000 рублей \n" +
                            "Пожалуйста, введите стоимость поездки еще раз.", tripId);
                }
                String mes = "Установлена стоимость проезда для одного человека: " + text;
                sendMessage(chatId, mes);
                TripActive trip = getTripActive(tripId);
                trip.setTripPrice(Integer.parseInt(text));
                tripActiveRepository.save(trip);
                thisState.setUserWriteLikeDriverComment(true);
                userStateRepository.save(thisState);
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
            } else if (thisState.isUserWriteLikeDriverTo() &&
                    thisState.isUserWriteLikeDriverWhen() &&
                    thisState.isUserWriteLikeDriverPrice() &&
                    thisState.isUserWriteLikeDriverHowMuchSits() &&
                    thisState.isUserWriteLikeDriverAuto() && thisState.isUserWriteLikeDriverComment()) {
                TripActive trip = getTripActive(tripId);
                trip.setComment(text);
                trip.setActive(true);
                tripActiveRepository.save(trip);
                thisState.setUserWriteLikeDriverTo(false);
                thisState.setUserWriteLikeDriverWhen(false);
                thisState.setUserWriteLikeDriver(false);
                thisState.setUserWriteLikeDriverPrice(false);
                thisState.setUserWriteLikeDriverHowMuchSits(false);
                thisState.setUserWriteLikeDriverAuto(false);
                thisState.setUserWriteLikeDriverComment(false);
                userStateRepository.save(thisState);
                sendMessage(chatId, "Ваша поездка спланирована, информацию о ней вы можете найти " +
                        "в меню, в разделе /history.");
                List<ActiveTripQuestions> questions = checkQuestionToSuitable(trip, trip.getCityFrom(),
                        trip.getCityTo());
                for (ActiveTripQuestions quest: questions) {
                    sendMessage(quest.getPassengerId(), "Здравствуйте! Возможно, мы нашли " +
                            "подходящую Вам поездку, посмотрите её! Чтобы забронировать поездку, нажмите" +
                            "\"Забронировать\". Если вы не хотите получать уведомления о подходящих " +
                            "поездках - удалите свою заявку в разделе /history.");
                    SendMessage message = sendTripInfo(quest.getPassengerId(), trip);
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
                    message.setReplyMarkup(markup);
                    executeMessage(message);
                }
            }
        }
    }

    private void askDriverWithDelete(Long chatId, String s, Long tripId, long messageId) {
        EditMessageText message = new EditMessageText();
        message.setText(s);
        message.setChatId(chatId);
        message.setMessageId((int)messageId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton stopWorkButton;
        stopWorkButton = InlineKeyboardButton.builder()
                .callbackData(STOP_WORK + "/" + tripId)
                .text("Отменить")
                .build();
        row.add(stopWorkButton);
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

    private void showDays(Long chatId, Long tripId, long messageId, String month) {
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setText("Выберите день месяца");
        message.setMessageId((int)messageId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        List<InlineKeyboardButton> row2 = new ArrayList<>();
        List<InlineKeyboardButton> row3 = new ArrayList<>();
        List<InlineKeyboardButton> row4 = new ArrayList<>();
        List<InlineKeyboardButton> row5 = new ArrayList<>();
        List<InlineKeyboardButton> down = new ArrayList<>();
        Calendar cal = Calendar.getInstance();
        Date date = new Date();
        int dayOfMonth = 1;
        if (Integer.parseInt(month) == date.getMonth()) {
            dayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
        }
        int maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int count = 0;
        for (int i = dayOfMonth; i <= maxDays; i++) {
            if (i>=0 && i<7) {
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row.add(day);
            } else if(i==7) {
                rowsLine.add(row);
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row2.add(day);
            }
            else if (i>7 && i<14) {
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row2.add(day);
            } else if(i==14) {
                rowsLine.add(row2);
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row3.add(day);
            }
            else if (i>14 && i<21) {
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row3.add(day);
            } else if(i==21) {
                rowsLine.add(row3);
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row4.add(day);
            }
            else if (i>21 && i<28) {
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row4.add(day);
            } else if(i==28) {
                rowsLine.add(row4);
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row5.add(day);
            }
            else if (i>28 && i < maxDays) {
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row5.add(day);
            } else if (i>28 && i == maxDays) {
                InlineKeyboardButton day = InlineKeyboardButton.builder()
                        .callbackData(DAY_NUMBER + "/" + i)
                        .text(String.valueOf(i))
                        .build();
                row5.add(day);
                rowsLine.add(row5);
            }
            count++;
        }
        InlineKeyboardButton stopWorkButton = InlineKeyboardButton.builder()
                .callbackData(STOP_WORK + "/" + tripId)
                .text("Отменить")
                .build();
        down.add(stopWorkButton);
        rowsLine.add(down);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        try{
            execute(message);
        }
        catch (TelegramApiException e) {
            log.error(ERROR_TEXT + e.getMessage());
        }
    }

    private void showMonths(Long chatId, Long tripId) {
        SendMessage message = new SendMessage();
        message.setText("Выберите месяц поездки");
        message.setChatId(chatId);
        int first = new Date().getMonth();
        int second = first + 1;
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        List<InlineKeyboardButton> down = new ArrayList<>();
        InlineKeyboardButton firstMonth = InlineKeyboardButton.builder()
                .callbackData(MONTH_NUMBER + "/" + first)
                .text(months.get(first))
                .build();
        InlineKeyboardButton secondMonth = InlineKeyboardButton.builder()
                .callbackData(MONTH_NUMBER + "/" + second)
                .text(months.get(second))
                .build();
        InlineKeyboardButton stopWorkButton = InlineKeyboardButton.builder()
                .callbackData(STOP_WORK + "/" + tripId)
                .text("Отменить")
                .build();
        row.add(firstMonth);
        row.add(secondMonth);
        down.add(stopWorkButton);
        rowsLine.add(row);
        rowsLine.add(down);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }

    private void passengerLogicImplementation(Update update, UserState thisState) {
        if (update.hasCallbackQuery()) {
            if (update.getCallbackQuery().getData().startsWith(STOP_WORK_PASSENGER)) {
                Long tripId = Long.valueOf(update.getCallbackQuery().getData().split("/")[1]);
                stopActiveQuestion(thisState, tripId, update.getCallbackQuery().getMessage().getMessageId());
            } else if (update.getCallbackQuery().getData().startsWith(MONTH_NUMBER)) {
                String monthOne = update.getCallbackQuery().getData().split("/")[1];
                String month = monthOne + ".";
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                long messageId = update.getCallbackQuery().getMessage().getMessageId();
                Long tripId = checkQuestionId(chatId);
                ActiveTripQuestions tripActive = getActiveQuestion(tripId);
                tripActive.setDateFormat(month);
                tripRepository.save(tripActive);
                showDays(chatId, tripId, messageId, monthOne);
            } else if (update.getCallbackQuery().getData().startsWith(DAY_NUMBER)) {
                Long chatId = update.getCallbackQuery().getMessage().getChatId();
                long messageId = update.getCallbackQuery().getMessage().getMessageId();
                Long tripId = checkQuestionId(chatId);
                ActiveTripQuestions tripActive = getActiveQuestion(tripId);
                StringBuilder stringBuilder = new StringBuilder(tripActive.getDateFormat());
                stringBuilder.append(update.getCallbackQuery().getData().split("/")[1] + "/");
                String date = String.valueOf(stringBuilder);
                tripActive.setDateFormat(date);
                tripRepository.save(tripActive);
                thisState.setUserWriteLikePassengerWhen(true);
                userStateRepository.save(thisState);
                String month = getActiveQuestion(tripId).getDateFormat().split("\\.")[0];
                String thisMonth = months.get(Integer.parseInt(month));
                String preDay = getActiveQuestion(tripId).getDateFormat().split("\\.")[1];
                String day = preDay.split("/")[0];
                String mes = "Ваша заявка на поездку сформирована и спланирована на: \n" +
                        "Месяц: " + thisMonth + "\n" +
                        "День: " + day +"\n";
                EditMessageText messageText = new EditMessageText();
                messageText.setText(mes);
                messageText.setChatId(chatId);
                messageText.setMessageId((int) messageId);
                try{
                    execute(messageText);
                }
                catch (TelegramApiException e) {
                    log.error(ERROR_TEXT + e.getMessage());
                }
                thisState.setUserWriteLikePassengerTo(false);
                thisState.setUserWriteLikePassengerWhen(false);
                thisState.setUserWriteLikePassenger(false);
                userStateRepository.save(thisState);
                String finalMes = "Вы можете посмотреть информацию о вашей заявке в разделе /history. " +
                        "Чтобы посмотреть подходящие Вам поездки, нажмите \"Найти поездку\"";
                SendMessage sendMessage = new SendMessage();
                sendMessage.setText(finalMes);
                sendMessage.setChatId(chatId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(SHOW_SUITABLE_TRIPS + "/" + tripId)
                        .text("Найти поездку")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                sendMessage.setReplyMarkup(markup);
                executeMessage(sendMessage);
                tripActive.setActive(true);
                tripRepository.save(tripActive);
            }
        } else {
            Long chatId = update.getMessage().getChatId();
            var tripId = checkQuestionId(chatId);
            if (!thisState.isUserWriteLikePassengerTo() && !thisState.isUserWriteLikePassengerWhen()) {
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
                thisState.setUserWriteLikePassengerTo(true);
                userStateRepository.save(thisState);
                sendInfoPhoto(chatId);
                askPassenger(chatId, INFO_PHOTO_TEXT, tripId);
            } else if (!thisState.isUserWriteLikePassengerWhen() && thisState.isUserWriteLikePassengerTo()) {
                Location location = update.getMessage().getLocation();
                String address = createLocationFromCoordinates(chatId, location);
                String mes = "Ищем поездку в: " + address;
                sendMessage(chatId, mes);
                ActiveTripQuestions trip = getActiveQuestion(tripId);
                trip.setCityTo(address);
                trip.setLongTo(location.getLongitude());
                trip.setLatTo(location.getLatitude());
                tripRepository.save(trip);
                showMonths(chatId, tripId);
                thisState.setUserWriteLikePassengerWhen(true);
                userStateRepository.save(thisState);
            }
            }
        }

    private void stopActiveQuestion(UserState thisState, Long tripId, Integer messageId) {
        System.out.println("hete");
        thisState.setUserWriteLikePassenger(false);
        thisState.setUserWriteLikePassengerTo(false);
        thisState.setUserWriteLikePassengerWhen(false);
        thisState.setUserWriteLikeDriver(false);
        thisState.setUserWriteLikeDriverTo(false);
        thisState.setUserWriteLikeDriverWhen(false);
        thisState.setUserWriteLikeDriverPrice(false);
        thisState.setUserWriteLikeDriverHowMuchSits(false);
        thisState.setUserWriteLikeDriverAuto(false);
        thisState.setUserWriteLikeDriverComment(false);
        thisState.setShowing(false);
        userStateRepository.save(thisState);
        if (!tripRepository.findById(tripId).isEmpty())
        tripRepository.deleteById(tripId);
        executeEditMessageText("Поездка отменена", thisState.getChatId(), messageId);
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
        Iterable<TripActive> all = tripActiveRepository.findAll();
        int i = 0;
        for (TripActive trip: all) {
            if (trip.isActive())
                i++;
        }
        return i;
    }
    private int activeQuestionTripCount() {
        int i =0;
        Iterable<ActiveTripQuestions> all = tripRepository.findAll();
        for (ActiveTripQuestions trip: all) {
            if (trip.isActive())
                i++;
        }
        return i;
    }
    private void usersSOUT(long chatId) {
        Iterable<User> users = userRepository.findAll();
        int i = 0;
        for (User user : users) {
            i++;
            String answer = i + ". Имя - " + user.getFirstName() + ", Логин - @" +
                    user.getUserName() +
                    ", Премиум? - " + user.isWhite() + ", Забанен? - " + user.isBan() +"\n";
            System.out.println(answer);
            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(answer);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton inlineKeyboardButtonDriver;
            InlineKeyboardButton inlineKeyboardButtonPassenger;
            if (user.isWhite()) {
                inlineKeyboardButtonDriver = InlineKeyboardButton.builder()
                        .callbackData(UNWHITE_USER + "/" + user.getCharId())
                        .text("Убрать Премиум")
                        .build();
            } else {
                inlineKeyboardButtonDriver = InlineKeyboardButton.builder()
                        .callbackData(WHITE_USER + "/" + user.getCharId())
                        .text("Добавить Премиум")
                        .build();
            }
            if (user.isBan()) {
                inlineKeyboardButtonPassenger = InlineKeyboardButton.builder()
                        .callbackData(UNBAN_USER + "/" + user.getCharId())
                        .text("Разбанить")
                        .build();
            } else {
                inlineKeyboardButtonPassenger = InlineKeyboardButton.builder()
                        .callbackData(BAN_USER + "/" + user.getCharId())
                        .text("Забанить")
                        .build();
            }
            row.add(inlineKeyboardButtonDriver);
            row.add(inlineKeyboardButtonPassenger);
            rowsLine.add(row);
            markup.setKeyboard(rowsLine);
            message.setReplyMarkup(markup);
            executeMessage(message);
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
            user.setBan(false);
            user.setWhite(false);

            userRepository.save(user);
            log.info("user saved: " + user);
        }
        if (getUserByChatId(message.getChatId()).getUserName() == null) {
            if (message.getChat().getUserName() == null) {
                sendMessage(message.getChatId(), "В Вашем телеграмм-аккаунте отсутствует имя пользователя." +
                        " Для более удобного использования бота, рекомендуем открыть \"Настройки\"" +
                        " в приложении телеграмм на Вашем устройстве и добавить имя пользователя.\n" +
                        "Если использовать бота, не имея имени пользователя, Ваши потенциальные попутчики" +
                        " не смогут связаться с Вами, когда увидят Вашу поездку или заявку.\n" +
                        "С уважением, администрация бота. \n" +
                        "!!!После добавления имени пользователя ОБЯЗАТЕЛЬНО перезапустите работу" +
                        " бота, нажав на кнопку /start!!!");
            } else {
                User user = getUserByChatId(message.getChatId());
                user.setUserName(message.getChat().getUserName());
                userRepository.save(user);
            }
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
                "Чтобы узнать причины бана, либо оспорить решение администрации, обратитесь к \n" +
                "администрации нашего канала - @bla_bla_krym_channel");
    }

    // Возвращает историю поездок пользователя
    private void getPassengerHistory(long chatId) {
        Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
        String answer = "Ваши заявки на поиск поездки:\n";
        sendMessage(chatId, answer);
        for (ActiveTripQuestions trip : trips) {
            if (trip.getPassengerId().equals(chatId)) {
                SendMessage sendMessage = new SendMessage();
                String month = trip.getDateFormat().split("\\.")[0];
                String thisMonth = months.get(Integer.parseInt(month));
                String secondSide = trip.getDateFormat().split("\\.")[1];
                String day = secondSide.split("/")[0];
                StringBuilder stringBuilder = new StringBuilder(EmojiParser.parseToUnicode("Дата" +
                        " поездки: \n" +
                        "Месяц" + ":date:" +": " + thisMonth + ", " +
                        "День: " + day +"\n"));
                stringBuilder.append(trip.getTripInfo());
                sendMessage.setText(String.valueOf(stringBuilder));
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
                String month = trip.getTripDate().split("\\.")[0];
                String thisMonth = months.get(Integer.parseInt(month));
                String secondSide = trip.getTripDate().split("\\.")[1];
                String day = secondSide.split("/")[0];
                String time = secondSide.split("/")[1];
                StringBuilder stringBuilder = new StringBuilder(EmojiParser.parseToUnicode("Дата и" +
                        " время поездки: \n" +
                        "Месяц" + ":date:" +": " + thisMonth + ", " +
                        "День: " + day +"\n" +
                        "Время" + ":stopwatch:" + ": " + time + "\n"));
                stringBuilder.append(trip.getTripInfo());
                if (trip.tripHasPassengers()) {
                    String finalMes = "Ваши пассажиры: ";
                    stringBuilder.append(finalMes);
                    List<String> passengersNames = trip.getPassengersNames();
                    for (int i = 0; i < passengersNames.size(); i++) {
                        stringBuilder.append("@" + passengersNames.get(i));
                    }
                }
                sendMessage.setChatId(chatId);
                sendMessage.setText(String.valueOf(stringBuilder));
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
    private void botLogicIfUpdateIsText(Update update, UserState thisState) {

        String messageText = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        if (messageText.contains("/send") && (checkAdmin(chatId))) {
            var textToSend = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")).trim());
            var users = userRepository.findAll();
            for (User user : users) {
                sendMessage(user.getCharId(), textToSend);
            }
            sendMessage(1313359155L, textToSend);
        } else if (messageText.contains("/ban") && (checkAdmin(chatId))) {
            var userName = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")).trim());
            User user = getUserByUserName(userName);
            banUser(chatId, user.getCharId());
        }
        else if (messageText.contains("/unban") && (checkAdmin(chatId))) {
            var userName = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")).trim());
            User user = getUserByUserName(userName);
            unbanUser(chatId, user.getCharId());
        }
        else if (messageText.contains("/prem") && (checkAdmin(chatId))) {
            var userName = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")).trim());
            User user = getUserByUserName(userName);
            addUserToWhiteLIst(chatId, user.getCharId());
        }
        else if (messageText.contains("/unprem") && (checkAdmin(chatId))) {
            var userName = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")).trim());
            User user = getUserByUserName(userName);
            removeUserFromWhiteLIst(chatId, user.getCharId());
        }
        else if (messageText.contains("/reset") && (checkAdmin(chatId))) {
            var userName = EmojiParser.parseToUnicode(messageText.substring(messageText.indexOf(" ")).trim());
            try {
                Long userId = getUserByUserName(userName).getCharId();
                userStateRepository.deleteById(userId);
                UserState state = new UserState();
                state.setChatId(userId);
                userStateRepository.save(state);
                sendMessage(chatId, "Параметры пользователя @" + userName + " сброшены.");
            } catch (Exception e) {
                sendMessage(chatId, "Данного пользователя нет в базе данных, либо" +
                        " что-то пошло не так, надо разбираться.");
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
                    getBookingTrips(chatId);
                    break;
                case "/help":
                    sendMessage(chatId, HELP_TEXT);
                    log.info("Request info");
                    break;
                case "/find":
                    if (isUserHasTripQuestion(chatId)) {
                        showAllTrips(chatId);
                        thisState.setShowing(true);
                        userStateRepository.save(thisState);
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

    private void botLogicIfUpdateIsCallback(Update update, UserState thisState) {
        String callbackData = update.getCallbackQuery().getData();
        long messageId = update.getCallbackQuery().getMessage().getMessageId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();

        if (callbackData.equals(DRIVER)) {
            if ((checkTripId(chatId) != null)) {
                String text = "Начинаем составлять поездку.";
                executeEditMessageText(text, chatId, messageId);
                sendInfoPhoto(chatId);
                askDriver(chatId, INFO_PHOTO_TEXT, messageId);
                thisState.setUserWriteLikeDriver(true);
                userStateRepository.save(thisState);
            } else sendMessage(chatId, "У вас превышено количество активных поездок.\n" +
                    "Для повышения допустимого количества активных поездок обратитесь к администрации " +
                    "нашего канала - @bla_bla_krym_channel.");
        } else if (callbackData.equals(PASSENGER)) {
            if ((checkQuestionId(chatId) != null)) {
                String text = "Начинаем составлять запрос на поездку:";
                executeEditMessageText(text, chatId, messageId);
                sendInfoPhoto(chatId);
                askPassenger(chatId, INFO_PHOTO_TEXT, messageId);
                thisState.setUserWriteLikePassenger(true);
                userStateRepository.save(thisState);
            } else sendMessage(chatId, "У вас превышено количество активных запросов на поездки.\n" +
                    "Для повышения допустимого количества активных запросов обратитесь к администрации \n" +
                    "нашего канала - @bla_bla_krym_channel.");
        } else if (callbackData.equals(SHOW_ALL_USERS) && (checkAdmin(chatId))) {
            usersSOUT(chatId);
        } else if (callbackData.startsWith(BAN_USER) && (checkAdmin(chatId))) {    //1
            Long userId = Long.valueOf(callbackData.split("/")[1]);
            banUser(chatId, userId);
        } else if (callbackData.startsWith(UNBAN_USER) && (checkAdmin(chatId))) {    //2
            Long userId = Long.valueOf(callbackData.split("/")[1]);
            unbanUser(chatId, userId);
        } else if (callbackData.startsWith(WHITE_USER) && (checkAdmin(chatId))) {    //3
            Long userId = Long.valueOf(callbackData.split("/")[1]);
            addUserToWhiteLIst(chatId, userId);
        } else if (callbackData.startsWith(UNWHITE_USER) && (checkAdmin(chatId))) {    //4
            Long userId = Long.valueOf(callbackData.split("/")[1]);
            removeUserFromWhiteLIst(chatId, userId);
        } else if (callbackData.equals(SHOW_FINAL_TRIPS) && (checkAdmin(chatId))) {
            showAllQuestionsToAdmin(chatId, messageId);
        }else if (callbackData.startsWith(SHOW_SUITABLE_TRIPS)) {
            Long questionId = Long.valueOf(callbackData.split("/")[1]);
            showSuitableTrips(chatId, questionId, messageId);
            thisState.setShowing(true);
            userStateRepository.save(thisState);
        } else if (callbackData.startsWith(ALL_TRIPS)) {
            String date = callbackData.split("/")[1];
            thisState.setShowing(false);
            userStateRepository.save(thisState);
            showAllTrips(chatId, messageId, date);
            thisState.setShowing(true);
            userStateRepository.save(thisState);
        }
        else if (callbackData.equals(SHOW_ACTIVE_TRIPS) && (checkAdmin(chatId))) {
            showAllTripsToAdmin(chatId, messageId);
        } else if (callbackData.startsWith(TO_BOOK) && thisState.isShowing()) {
            Long tripId = Long.valueOf(callbackData.split("/")[1]);
            TripActive trip = getTripActive(tripId);
            int countOfSits = trip.getCountOfSits();
            thisState.setShowing(false);
            userStateRepository.save(thisState);
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
        else if (callbackData.startsWith(STOP_WORK)) {
            Long tripId = Long.valueOf(callbackData.split("/")[1]);
            stopActiveTrip(thisState, tripId, messageId);
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

    private void stopActiveTrip(UserState thisState, Long tripId, long messageId) {
        thisState.setUserWriteLikePassenger(false);
        thisState.setUserWriteLikePassengerTo(false);
        thisState.setUserWriteLikePassengerWhen(false);
        thisState.setUserWriteLikeDriver(false);
        thisState.setUserWriteLikeDriverTo(false);
        thisState.setUserWriteLikeDriverWhen(false);
        thisState.setUserWriteLikeDriverPrice(false);
        thisState.setUserWriteLikeDriverHowMuchSits(false);
        thisState.setUserWriteLikeDriverAuto(false);
        thisState.setUserWriteLikeDriverComment(false);
        thisState.setUserWriteLikeDriverMonth(false);
        thisState.setUserWriteLikeDriverDay(false);
        thisState.setShowing(false);
        userStateRepository.save(thisState);
        if (!tripActiveRepository.findById(tripId).isEmpty())
        tripActiveRepository.deleteById(tripId);
        executeEditMessageText("Поездка отменена", thisState.getChatId(), messageId);
    }

    private void showSuitableTrips(Long chatId, Long questionId, long messageId) {
        String finalMes = "Чтобы забронировать поездку нажмите кнопку \"Забронировать\" " +
                "и выберите количество необходимых мест.";
        EditMessageText sendMessage = new EditMessageText();
        sendMessage.setChatId(chatId);
        sendMessage.setText(finalMes);
        sendMessage.setMessageId((int) messageId);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        ActiveTripQuestions question = getActiveQuestion(questionId);
        var trips = checkTripsToSuitable(question, question.getCityFrom(), question.getCityTo());
        for (TripActive trip : trips) {
            if ((trip.isActive()) && (trip.getCountOfSits() > 0)
                //&& !(trip.getPassengers().contains(chatId)) && (trip.getDriverId() != chatId)
            ) {
                SendMessage message = new SendMessage();
                User driver = getUserByChatId(trip.getDriver());
                String month = trip.getTripDate().split("\\.")[0];
                String thisMonth = months.get(Integer.parseInt(month));
                String secondSide = trip.getTripDate().split("\\.")[1];
                String day = secondSide.split("/")[0];
                String time = secondSide.split("/")[1];
                StringBuilder stringBuilder = new StringBuilder(EmojiParser.parseToUnicode("Выбрана дата и" +
                        " время поездки: \n" +
                        "Месяц" + ":date:" +": " + thisMonth + ", " +
                        "День: " + day +"\n" +
                        "Время" + ":stopwatch:" + ": " + time + "\n"));
                stringBuilder.append(trip.getTripInfo());
                if (driver.getReviewCount() >= 5) {
                    stringBuilder.append("\n Рейтинг водителя: " + driver.getRating());
                    message.setText(String.valueOf(stringBuilder));
                } else {
                    message.setText(String.valueOf(stringBuilder));
                }
                message.setChatId(chatId);
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
                message.setReplyMarkup(markup);
                executeMessage(message);
            }
        }
        String mes = "Если данные поездки Вам не подходят, или же отсутствуют, то нажмите кнопку \"Все поездки\"";
        SendMessage newMessage = new SendMessage();
        newMessage.setText(mes);
        newMessage.setChatId(chatId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> upRow = new ArrayList<>();
        InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                .callbackData(ALL_TRIPS + "/" + question.getDateFormat())
                .text("Все поездки")
                .build();
        upRow.add(inlineKeyboardButtonNoComment);
        rowsLine.add(upRow);
        markup.setKeyboard(rowsLine);
        newMessage.setReplyMarkup(markup);
        executeMessage(newMessage);
    }

    private void deleteBookFromUser(long chatId, Long idDeletedBook, long messageId) {
        TripActive tripActive = getTripActive(idDeletedBook);
        User driver = getUserByChatId(tripActive.getDriverId());
        User user = getUserByChatId(chatId);
        sendMessage(driver.getCharId(), "Ваш пассажир @" + user.getUserName() + " отменил бронирование" +
                "поездки, количество доступных мест восстановлено.");
        String text = "Бронирование отменено.";
        int countOfSits = 0;
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
        TripActive tripActive = getTripActive(idDeletedTrip);
        if (tripActive.tripHasPassengers()) {
            List<String> passengers = tripActive.getPassengers();
            for (String idStr : passengers) {
                Long passengerId = Long.valueOf(idStr.split("_")[0]);
                sendMessage(passengerId, "Ваша забронированная поездка из " +
                        tripActive.getCityFrom() + " в " + tripActive.getCityTo() + " была удалена " +
                        "водителем.");
                User passenger = getUserByChatId(passengerId);
                passenger.deleteMyBookingTrip(String.valueOf(idDeletedTrip));
                userRepository.save(passenger);
            }
        }
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
    private User getUserByUserName(String userName) {
        Iterable<User> all = userRepository.findAll();
        for (User user: all) {
            if (user.getUserName().equals(userName)) {
                return user;
            }
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

    private void showAllTrips(Long chatId, long messageId, String date) {
        String finalMes = "Чтобы забронировать поездку нажмите кнопку \"Забронировать\" " +
                "и выберите количество необходимых мест.";
        EditMessageText sendMessage = new EditMessageText();
        sendMessage.setText(finalMes);
        sendMessage.setChatId(chatId);
        sendMessage.setMessageId((int) messageId);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        var trips = tripActiveRepository.findAll();
        for (TripActive trip : trips) {
            if ((trip.isActive()) && (trip.getCountOfSits() > 0) &&
                    (trip.getTripDate().split("/")[0].equals(date))
                //&& !(trip.getPassengers().contains(chatId)) && (trip.getDriverId() != chatId)
            ) {
                SendMessage message = sendTripInfo(chatId, trip);
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
                message.setReplyMarkup(markup);
                executeMessage(message);
            }
        }
    }
    private SendMessage sendTripInfo(Long chatId, TripActive trip) {
        SendMessage message = new SendMessage();
        User driver = getUserByChatId(trip.getDriver());
        String month = trip.getTripDate().split("\\.")[0];
        String thisMonth = months.get(Integer.parseInt(month));
        String secondSide = trip.getTripDate().split("\\.")[1];
        String day = secondSide.split("/")[0];
        String time = secondSide.split("/")[1];
        StringBuilder stringBuilder = new StringBuilder(EmojiParser.parseToUnicode("Выбрана дата и" +
                " время поездки: \n" +
                "Месяц" + ":date:" +": " + thisMonth + ", " +
                "День: " + day +"\n" +
                "Время" + ":stopwatch:" + ": " + time + "\n"));
        stringBuilder.append(trip.getTripInfo());
        if (driver.getReviewCount() >= 5) {
            stringBuilder.append("\n Рейтинг водителя: " + driver.getRating());
            message.setText(String.valueOf(stringBuilder));
        } else {
            message.setText(String.valueOf(stringBuilder));
        }
        message.setChatId(chatId);
        return message;
    }
    private void showAllQuestionsToAdmin(Long chatId, long messageId) {
        String finalMes = "Чтобы удалить запрос, нажми \"Удалить\"";
        EditMessageText sendMessage = new EditMessageText();
        sendMessage.setText(finalMes);
        sendMessage.setChatId(chatId);
        sendMessage.setMessageId((int) messageId);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        var trips = tripRepository.findAll();
        for (ActiveTripQuestions trip : trips) {
            if ((trip.isActive())) {
                String month = trip.getDateFormat().split("\\.")[0];
                String thisMonth = months.get(Integer.parseInt(month));
                String secondSide = trip.getDateFormat().split("\\.")[1];
                String day = secondSide.split("/")[0];
                StringBuilder stringBuilder = new StringBuilder(EmojiParser.parseToUnicode("Дата" +
                        " поездки: \n" +
                        "Месяц" + ":date:" +": " + thisMonth + ", " +
                        "День: " + day +"\n"));
                stringBuilder.append(trip.getTripInfo());
                SendMessage message = new SendMessage();
                message.setText(String.valueOf(stringBuilder));
                message.setChatId(chatId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton deleteTrip = InlineKeyboardButton.builder()
                        .callbackData(DELETE_QUESTION + "/" + trip.getTripId())
                        .text("Удалить")
                        .build();
                upRow.add(deleteTrip);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                message.setReplyMarkup(markup);
                executeMessage(message);
            }
        }
    }
    private void showAllTripsToAdmin(Long chatId, long messageId) {
        String finalMes = "Чтобы забронировать поездку нажмите кнопку \"Забронировать\" " +
                "и выберите количество необходимых мест.\n" +
                "Чтобы удалить поездку, нажми удалить \"Удалить\"";
        EditMessageText sendMessage = new EditMessageText();
        sendMessage.setText(finalMes);
        sendMessage.setChatId(chatId);
        sendMessage.setMessageId((int) messageId);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        var trips = tripActiveRepository.findAll();
        for (TripActive trip : trips) {
            if ((trip.isActive())            ) {
                SendMessage message = sendTripInfo(chatId, trip);
                message.setChatId(chatId);
                InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
                List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
                List<InlineKeyboardButton> upRow = new ArrayList<>();
                InlineKeyboardButton inlineKeyboardButtonNoComment = InlineKeyboardButton.builder()
                        .callbackData(TO_BOOK + "/" + trip.getTripId())
                        .text("Забронировать")
                        .build();
                InlineKeyboardButton deleteTrip = InlineKeyboardButton.builder()
                        .callbackData(DELETE_TRIP + "/" + trip.getTripId())
                        .text("Удалить")
                        .build();
                upRow.add(inlineKeyboardButtonNoComment);
                upRow.add(deleteTrip);
                rowsLine.add(upRow);
                markup.setKeyboard(rowsLine);
                message.setReplyMarkup(markup);
                executeMessage(message);
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
                SendMessage sendMessage = sendTripInfo(chatId, trip);
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
        int maxTrips = 5;
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
    private void addUserToWhiteLIst(Long chatId, Long userId) {
        User user = getUserByChatId(userId);
        user.setWhite(true);
        userRepository.save(user);
        sendMessage(chatId, "Пользователю @" + user.getUserName() + " добавлена подписка Премиум");
    }
    private void removeUserFromWhiteLIst(Long chatId, Long userId) {
        User user = getUserByChatId(userId);
        user.setWhite(false);
        userRepository.save(user);
        sendMessage(chatId, "У пользователя @" + user.getUserName() + " удалена подписка Премиум");
    }
    private void banUser(Long chatId, Long userId) {
        User user = getUserByChatId(userId);
        user.setBan(true);
        userRepository.save(user);
        sendMessage(chatId, "Пользователь @" + user.getUserName() + " забанен");
    }
    private void unbanUser(Long chatId, Long userId) {
        User user = getUserByChatId(userId);
        user.setBan(false);
        userRepository.save(user);
        sendMessage(chatId, "Пользователь @" + user.getUserName() + " разбанен");
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
        askAboutTrip();
        deleteActiveTrips();
        remindAboutTrips();
        sendMessage(1313359155, "Завершенные поездки или поездки с истекшим временем были удалены.");
    }

    private void askAboutTrip() throws ParseException {
        Calendar cal = Calendar.getInstance();
        int thisDay = cal.get(Calendar.DAY_OF_MONTH);
        int thisMonth = cal.get(Calendar.MONTH);
        String tripDate;
        int tripDay;
        int tripMonth;
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        for (TripActive trip : tripsActive) {
            tripDate = trip.getTripDate().split("/")[0];
            tripMonth = Integer.parseInt(tripDate.split("\\.")[0]);
            tripDay = Integer.parseInt(tripDate.split("\\.")[1]);
            if (!trip.isActive()) {
                tripActiveRepository.deleteById(trip.getTripId());
            }
            if ((tripMonth == thisMonth && tripDay < thisDay) || (tripMonth < thisMonth)) {
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
        Calendar cal = Calendar.getInstance();
        int thisDay = cal.get(Calendar.DAY_OF_MONTH);
        int thisMonth = cal.get(Calendar.MONTH);
        String questDate;
        int questDay;
        int questMonth;
        String tripDate;
        int tripDay;
        int tripMonth;
        Iterable<ActiveTripQuestions> trips = tripRepository.findAll();
        List<Long> deletedIds = new ArrayList<>();
        for (ActiveTripQuestions trip : trips) {
            questDate = trip.getDateFormat().split("/")[0];
            questMonth = Integer.parseInt(questDate.split("\\.")[0]);
            questDay = Integer.parseInt(questDate.split("\\.")[1]);
            if ((questMonth == thisMonth && questDay < thisDay) || (questMonth < thisMonth)) {
                System.out.println("Here2");
                deletedIds.add(trip.getTripId());
            }
        }
        for (Long id: deletedIds) {
            tripRepository.deleteById(id);
        }
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        List<Long> deletedIdsTrips = new ArrayList<>();
        for (TripActive trip : tripsActive) {
            tripDate = trip.getTripDate().split("/")[0];
            tripMonth = Integer.parseInt(tripDate.split("\\.")[0]);
            tripDay = Integer.parseInt(tripDate.split("\\.")[1]);
            if ((tripMonth == thisMonth && tripDay < thisDay) || (tripMonth < thisMonth)) {
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
        Calendar cal = Calendar.getInstance();
        int thisDay = cal.get(Calendar.DAY_OF_MONTH);
        int thisMonth = cal.get(Calendar.MONTH);
        String tripDate;
        int tripMonth;
        int tripDay;
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        for (TripActive trip : tripsActive) {
            tripDate = trip.getTripDate().split("/")[0];
            tripMonth = Integer.parseInt(tripDate.split("\\.")[0]);
            tripDay = Integer.parseInt(tripDate.split("\\.")[1]);
            if (tripMonth == thisMonth && tripDay == thisDay) {
                List<String> passengersId = trip.getPassengers();
                for (int i = 0; i < passengersId.size(); i++) {
                    User user = getUserByChatId(Long.parseLong(passengersId.get(i).split("_")[0]));
                    String time = trip.getTripDate().split("/")[1];
                    String driverName = findUserNameById(trip.getDriver());
                    sendMessage(user.getCharId(), "Напоминаю Вам о спланированной сегодня поездке в "
                            + time +", для связи можете написать водителю: @" + driverName);
                    sendMessage(trip.getDriverId(), "Напоминаю Вам о спланированной сегодня поездке в "
                            + time);
                }
            }
        }
    }
    private List<ActiveTripQuestions> checkQuestionToSuitable(TripActive tripActive, String cityFrom,
                                                              String cityTo) {
        Location tripFrom = createMyLocation(tripActive.getLongFrom(), tripActive.getLatFrom());
        Location tripTo = createMyLocation(tripActive.getLongTo(), tripActive.getLatTo());
        List<ActiveTripQuestions> questions = new ArrayList<>();
        MyRouter router = new MyRouter();
        Iterable<ActiveTripQuestions> activeTripQuestions = tripRepository.findAll();
        for (ActiveTripQuestions quest: activeTripQuestions) {
            if (tripActive.getTripDate().split("/")[0].equals(quest.getDateFormat().split("/")[0])) {
                if (quest.getCityFrom().equals(cityFrom) && quest.getCityTo().equals(cityTo)) {
                    questions.add(quest);
                } else if (quest.getCityFrom().equals(cityFrom) && !quest.getCityTo().equals(cityTo)) {
                    Location pasFrom = createMyLocation(quest.getLongFrom(), quest.getLatFrom());
                    Location pasTo = createMyLocation(quest.getLongTo(), quest.getLatTo());
                    if (router.isSuitableTo(pasTo, tripFrom, tripTo)) {
                        questions.add(quest);
                    }
                } else if (!quest.getCityFrom().equals(cityFrom) && quest.getCityTo().equals(cityTo)) {
                    Location pasFrom = createMyLocation(quest.getLongFrom(), quest.getLatFrom());
                    Location pasTo = createMyLocation(quest.getLongTo(), quest.getLatTo());
                    if (router.isSuitableFrom(pasFrom, tripFrom, tripTo)) {
                        questions.add(quest);
                    }
                } else if (!quest.getCityFrom().equals(cityFrom) && !quest.getCityTo().equals(cityTo)) {
                    Location pasFrom = createMyLocation(quest.getLongFrom(), quest.getLatFrom());
                    Location pasTo = createMyLocation(quest.getLongTo(), quest.getLatTo());
                    if (router.isSuitableTrip(pasFrom, pasTo, tripFrom, tripTo)) {
                        questions.add(quest);
                    }
                }
            }
        }
        return questions;
    }
    private List<TripActive> checkTripsToSuitable(ActiveTripQuestions question, String cityFrom, String cityTo) {
        Location pasFrom = createMyLocation(question.getLongFrom(), question.getLatFrom());
        Location pasTo = createMyLocation(question.getLongTo(), question.getLatTo());
        List<TripActive> trips = new ArrayList<>();
        MyRouter router = new MyRouter();
        Iterable<TripActive> tripsActive = tripActiveRepository.findAll();
        System.out.println("hi im here");
        for (TripActive trip: tripsActive) {
            if (question.getDateFormat().split("/")[0].equals(trip.getTripDate().split("/")[0])) {
                try {
                    if (trip.getCityFrom().equals(cityFrom) && trip.getCityTo().equals(cityTo)) {
                        System.out.println("yes1");
                        trips.add(trip);
                    } else if (trip.getCityFrom().equals(cityFrom) && !trip.getCityTo().equals(cityTo)) {
                        Location tripFrom = createMyLocation(trip.getLongFrom(), trip.getLatFrom());
                        Location tripTo = createMyLocation(trip.getLongTo(), trip.getLatTo());
                        if (router.isSuitableTo(pasTo, tripFrom, tripTo)) {
                            System.out.println("yes2");
                            trips.add(trip);
                        }
                    } else if (!trip.getCityFrom().equals(cityFrom) && trip.getCityTo().equals(cityTo)) {
                        Location tripFrom = createMyLocation(trip.getLongFrom(), trip.getLatFrom());
                        Location tripTo = createMyLocation(trip.getLongTo(), trip.getLatTo());
                        if (router.isSuitableFrom(pasFrom, tripFrom, tripTo)) {
                            System.out.println("yes3");
                            trips.add(trip);
                        }
                    } else if (!trip.getCityFrom().equals(cityFrom) && !trip.getCityTo().equals(cityTo)) {
                        Location tripFrom = createMyLocation(trip.getLongFrom(), trip.getLatFrom());
                        Location tripTo = createMyLocation(trip.getLongTo(), trip.getLatTo());
                        if (router.isSuitableTrip(pasFrom, pasTo, tripFrom, tripTo)) {
                            System.out.println("yes4");
                            trips.add(trip);
                        }
                    }
                } catch (Exception e) {
                    System.out.println("some exception");
                }
            }
        }
        for (TripActive trip: trips) {
            System.out.println(trip.getTripId());
        }
        return trips;
    }
    private Location createMyLocation (Double lon, Double lat) {
        Location location = new Location();
        location.setLatitude(lat);
        location.setLongitude(lon);
        return location;
    }
    private boolean checkUserStatus(Long userId) {
        GetChatMember getMember = new GetChatMember();
        getMember.setUserId(userId);
        getMember.setChatId("-1001925872051");
        ChatMember theChatMember;
        try {
            theChatMember = execute(getMember);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
        if (theChatMember.getStatus().equals("administrator")
        || theChatMember.getStatus().equals("creator")
        || theChatMember.getStatus().equals("member")) {
            return true;
        } else return false;
    }
    private void askDriver(Long chatId, String text, Long tripId) {
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatId);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
            List<InlineKeyboardButton> row = new ArrayList<>();
            InlineKeyboardButton stopWorkButton;
                stopWorkButton = InlineKeyboardButton.builder()
                        .callbackData(STOP_WORK + "/" + tripId)
                        .text("Отменить")
                        .build();
            row.add(stopWorkButton);
            rowsLine.add(row);
            markup.setKeyboard(rowsLine);
            message.setReplyMarkup(markup);
            executeMessage(message);
        }
    private void askPassenger(Long chatId, String text, Long tripId) {
        SendMessage message = new SendMessage();
        message.setText(text);
        message.setChatId(chatId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsLine = new ArrayList<>();
        List<InlineKeyboardButton> row = new ArrayList<>();
        InlineKeyboardButton stopWorkButton;
        stopWorkButton = InlineKeyboardButton.builder()
                .callbackData(STOP_WORK_PASSENGER + "/" + tripId)
                .text("Отменить")
                .build();
        row.add(stopWorkButton);
        rowsLine.add(row);
        markup.setKeyboard(rowsLine);
        message.setReplyMarkup(markup);
        executeMessage(message);
    }
    private void sendInfoPhoto(Long chatId) {
        File file = new File("help.jpg");
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setPhoto(new InputFile(file));
        sendPhoto.setChatId(chatId);
        try {
            execute(sendPhoto);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
