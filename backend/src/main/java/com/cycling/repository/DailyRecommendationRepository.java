package com.cycling.repository;

import com.cycling.model.DailyRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DailyRecommendationRepository extends JpaRepository<DailyRecommendation, Long> {

    /** Die (einzige) aktuelle Empfehlung – wir halten bewusst nur eine Zeile. */
    Optional<DailyRecommendation> findTopByOrderByIdAsc();
}
