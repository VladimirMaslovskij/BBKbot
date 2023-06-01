package io.proj3ct.TestBlaBlaBot.model;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name="userStateTable")
public class UserState {
    @Id
    private Long chatId;

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    private boolean isUserWriteLikePassenger;
    private boolean isUserWriteLikePassengerTo;
    private boolean isUserWriteLikePassengerWhen;
    private boolean isUserWriteLikeDriver;
    private boolean isUserWriteLikeDriverTo;
    private boolean isUserWriteLikeDriverWhen;
    private boolean isUserWriteLikeDriverPrice;
    private boolean isUserWriteLikeDriverHowMuchSits;
    private boolean isUserWriteLikeDriverAuto;
    private boolean isUserWriteLikeDriverComment;
    private boolean isUserWriteLikeDriverMonth;
    private boolean isUserWriteLikeDriverDay;
    private boolean isShowing;

    public UserState() {
        this.isUserWriteLikePassenger = false;
        this.isUserWriteLikePassengerTo = false;
        this.isUserWriteLikePassengerWhen = false;
        this.isUserWriteLikeDriver = false;
        this.isUserWriteLikeDriverTo = false;
        this.isUserWriteLikeDriverWhen = false;
        this.isUserWriteLikeDriverPrice = false;
        this.isUserWriteLikeDriverHowMuchSits = false;
        this.isUserWriteLikeDriverAuto = false;
        this.isUserWriteLikeDriverComment = false;
        this.isUserWriteLikeDriverMonth = false;
        this.isUserWriteLikeDriverDay = false;
        this.isShowing = false;
    }

    public boolean isUserWriteLikeDriverMonth() {
        return isUserWriteLikeDriverMonth;
    }

    public void setUserWriteLikeDriverMonth(boolean userWriteLikeDriverMonth) {
        isUserWriteLikeDriverMonth = userWriteLikeDriverMonth;
    }

    public boolean isUserWriteLikeDriverDay() {
        return isUserWriteLikeDriverDay;
    }

    public void setUserWriteLikeDriverDay(boolean userWriteLikeDriverDay) {
        isUserWriteLikeDriverDay = userWriteLikeDriverDay;
    }

    public boolean isUserWriteLikePassenger() {
        return isUserWriteLikePassenger;
    }

    public void setUserWriteLikePassenger(boolean userWriteLikePassenger) {
        isUserWriteLikePassenger = userWriteLikePassenger;
    }

    public boolean isUserWriteLikePassengerTo() {
        return isUserWriteLikePassengerTo;
    }

    public void setUserWriteLikePassengerTo(boolean userWriteLikePassengerTo) {
        isUserWriteLikePassengerTo = userWriteLikePassengerTo;
    }

    public boolean isUserWriteLikePassengerWhen() {
        return isUserWriteLikePassengerWhen;
    }

    public void setUserWriteLikePassengerWhen(boolean userWriteLikePassengerWhen) {
        isUserWriteLikePassengerWhen = userWriteLikePassengerWhen;
    }

    public boolean isUserWriteLikeDriver() {
        return isUserWriteLikeDriver;
    }

    public void setUserWriteLikeDriver(boolean userWriteLikeDriver) {
        isUserWriteLikeDriver = userWriteLikeDriver;
    }

    public boolean isUserWriteLikeDriverTo() {
        return isUserWriteLikeDriverTo;
    }

    public void setUserWriteLikeDriverTo(boolean userWriteLikeDriverTo) {
        isUserWriteLikeDriverTo = userWriteLikeDriverTo;
    }

    public boolean isUserWriteLikeDriverWhen() {
        return isUserWriteLikeDriverWhen;
    }

    public void setUserWriteLikeDriverWhen(boolean userWriteLikeDriverWhen) {
        isUserWriteLikeDriverWhen = userWriteLikeDriverWhen;
    }

    public boolean isUserWriteLikeDriverPrice() {
        return isUserWriteLikeDriverPrice;
    }

    public void setUserWriteLikeDriverPrice(boolean userWriteLikeDriverPrice) {
        isUserWriteLikeDriverPrice = userWriteLikeDriverPrice;
    }

    public boolean isUserWriteLikeDriverHowMuchSits() {
        return isUserWriteLikeDriverHowMuchSits;
    }

    public void setUserWriteLikeDriverHowMuchSits(boolean userWriteLikeDriverHowMuchSits) {
        isUserWriteLikeDriverHowMuchSits = userWriteLikeDriverHowMuchSits;
    }

    public boolean isUserWriteLikeDriverAuto() {
        return isUserWriteLikeDriverAuto;
    }

    public void setUserWriteLikeDriverAuto(boolean userWriteLikeDriverAuto) {
        isUserWriteLikeDriverAuto = userWriteLikeDriverAuto;
    }

    public boolean isUserWriteLikeDriverComment() {
        return isUserWriteLikeDriverComment;
    }

    public void setUserWriteLikeDriverComment(boolean userWriteLikeDriverComment) {
        isUserWriteLikeDriverComment = userWriteLikeDriverComment;
    }

    public boolean isShowing() {
        return isShowing;
    }

    public void setShowing(boolean showing) {
        isShowing = showing;
    }
}
