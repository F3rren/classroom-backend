package com.classroom.booking.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
@Data
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class BookingOwner {

    @Column(name = "user_id", nullable = false)
    private Long id;

    @Column(name = "user_username", length = 50)
    private String username;

    @Column(name = "user_name", length = 100)
    private String name;
}
