package io.proj3ct.TestBlaBlaBot.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Entity(name = "trip_active_table")
public class TripActive {
    @Id
    private Long tripId;
    private Long driverId;
    private String cityFrom;
    private String cityTo;
    private String tripDate;
    private int countOfSits;
    private int tripPrice;
    private String auto;
    private String comment;
    private Double longFrom;
    private Double latFrom;
    private Double longTo;
    private Double latTo;
    private String passengers;
    private boolean isActive;
    public String getTripInfo() {
        String[] dateTime = this.getTripDate().split("/");
        StringBuilder str = new StringBuilder("Дата: " + dateTime[0] + ";\n");
        str.append("Время: " + dateTime[1] + ";\n");
        str.append("Откуда: " + this.getCityFrom() + ";\n");
        str.append("Куда: " + this.getCityTo() + ".\n");
        str.append("Автомобиль: " + this.getAuto() + ".\n");
        str.append("Количество мест: " + this.getCountOfSits() + ".\n");
        str.append("Стоимость за место: " + this.getTripPrice() + ".\n");
        if (this.comment != null)
            str.append("Пожелания к поездке: " + this.getComment() + ".\n");
        String message = String.valueOf(str);
        return message;
    }


    public List<String> getPassengers() {
        List<String> people = List.of(this.passengers.split("/"));
        return people;
    }

    public void addPassengers(String passengers) {
        this.passengers = this.passengers + passengers + "/";
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

    public Long getDriverId() {
        return driverId;
    }

    public void setDriverId(Long driverId) {
        this.driverId = driverId;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getAuto() {
        return auto;
    }

    public void setAuto(String auto) {
        this.auto = auto;
    }

    public Long getTripId() {
        return tripId;
    }

    public void setTripId(Long tripId) {
        this.tripId = tripId;
    }

    public Long getDriver() {
        return driverId;
    }

    public void setDriver(Long driverId) {
        this.driverId = driverId;
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

    public String getTripDate() {
        return tripDate;
    }

    public void setTripDate(String tripDate) {
        this.tripDate = tripDate;
    }

    public int getCountOfSits() {
        return countOfSits;
    }

    public void setCountOfSits(int countOfSits) {
        this.countOfSits = countOfSits;
    }

    public int getTripPrice() {
        return tripPrice;
    }

    public void setTripPrice(int tripPrice) {
        this.tripPrice = tripPrice;
    }
}
