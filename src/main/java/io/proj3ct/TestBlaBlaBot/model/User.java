package io.proj3ct.TestBlaBlaBot.model;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.util.Set;

@Entity(name="usersDataTable")
public class User {

    @Override
    public String toString() {
        return "User{" +
                "charId=" + charId +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", userName='" + userName + '\'' +
                ", registeredAt=" + registeredAt +
                '}';
    }

    @Id
    private Long charId;

    private String firstName;
    private String lastName;
    private String userName;
    private Timestamp registeredAt;
    private int reviewCount;
    private int reviewSum;
    private Double rating;
    private boolean isWhite;
    private boolean isBan;

    public boolean isWhite() {
        return isWhite;
    }

    public void setWhite(boolean white) {
        isWhite = white;
    }

    public boolean isBan() {
        return isBan;
    }

    public void setBan(boolean ban) {
        isBan = ban;
    }

    private void setRating(int review) {
        this.reviewCount++;
        this.reviewSum = reviewSum + review;
        this.rating = (double) reviewSum / (double) reviewCount;
    }
    private String getRating() {
        return new DecimalFormat("#0.00").format(this.rating);
    }


    public Long getCharId() {
        return charId;
    }

    public void setCharId(Long charId) {
        this.charId = charId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public Timestamp getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(Timestamp registeredAt) {
        this.registeredAt = registeredAt;
    }
}
