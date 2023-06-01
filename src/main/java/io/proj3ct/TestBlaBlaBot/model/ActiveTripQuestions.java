package io.proj3ct.TestBlaBlaBot.model;

import com.vdurmont.emoji.EmojiParser;
import org.telegram.telegrambots.meta.api.objects.Location;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "activeTripDataTable")

public class ActiveTripQuestions {
    @Id
    private Long tripId;
    private Long passengerId;

    private String cityFrom;
    private String cityTo;
    private String dateFormat;

    private Double longFrom;
    private Double latFrom;
    private Double longTo;
    private Double latTo;
    private boolean isActive;
    public String getTripInfo() {
        StringBuilder str = new StringBuilder(EmojiParser.parseToUnicode("Откуда" +
                ":round_pushpin:" + ": " +
                this.getCityFrom() + ";\n" +
                "Куда" + ":round_pushpin:" + ": " + this.getCityTo() + ".\n"));
        String message = String.valueOf(str);
        return message;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public Double getLongFrom() {
        return longFrom;
    }

    public void setLongFrom(Double longFrom) {
        this.longFrom = longFrom;
    }

    public Double getLatFrom() {
        return latFrom;
    }

    public void setLatFrom(Double latFrom) {
        this.latFrom = latFrom;
    }

    public Double getLongTo() {
        return longTo;
    }

    public void setLongTo(Double longTo) {
        this.longTo = longTo;
    }

    public Double getLatTo() {
        return latTo;
    }

    public void setLatTo(Double latTo) {
        this.latTo = latTo;
    }

    public String  printTrip(String userName) {
        String str = "Попутчик найден.\n" +
                "Хочет уехать из : " + cityFrom + "\n" +
                "В : " + cityTo + "\n" +
                "Дата : " + dateFormat + "\n" +
                "Телеграм пассажира : @" + userName;
        return str;
    }

    public Long getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(Long passengerId) {
        this.passengerId = passengerId;
    }
    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public String getCityFrom() {
        return cityFrom;
    }

    public void setCityFrom(String cityFrom) {
        this.cityFrom = cityFrom;
    }

    public String getCityTo() {
        return cityTo;
    }

    public void setCityTo(String cityTo) {
        this.cityTo = cityTo;
    }

    public String getDateFormat() {
        return dateFormat;
    }

    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }
}
