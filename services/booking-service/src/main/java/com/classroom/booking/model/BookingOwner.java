package com.classroom.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Who made a booking, as a snapshot taken at the moment of booking.
 *
 * It used to be a @ManyToOne to the User entity. That relation crosses the boundary between
 * two services: users belong to auth-service, bookings do not. Had it stayed a join, every
 * read of a booking would have required a network call.
 *
 * The three fields are exactly the ones sanitizeOwnerForListing already exposed in the JSON
 * (id, username, name), so the shape of the response does not change: it is an @Embeddable
 * and not an entity precisely so it keeps serialising as a nested "user" object. The email,
 * the role and the login dates were never exposed and still are not.
 *
 * It is a snapshot by choice: it shows who booked AS THEY WERE THEN. If the user changes
 * their name, history is not rewritten. For the same reason these fields must not be
 * resynchronised when auth-service updates a profile.
 */
@Embeddable
public class BookingOwner {

    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(name = "user_username", length = 50)
    private String username;

    @Column(name = "user_name", length = 100)
    private String name;

    public BookingOwner() {
    }

    public BookingOwner(Long id, String username, String name) {
        this.id = id;
        this.username = username;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
