package com.prenotazioni.repository;

import com.prenotazioni.model.Corso;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ICorsoRepository extends JpaRepository<Corso, Long> {}
