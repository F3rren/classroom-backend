package com.prenotazioni.booking.repository;

import com.prenotazioni.booking.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseRepository extends JpaRepository<Course, Long> {}
