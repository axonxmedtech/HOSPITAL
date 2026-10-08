package com.hms.repository;

import com.hms.entity.ConsultationStatement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConsultationStatementRepository extends JpaRepository<ConsultationStatement, Long> {

    List<ConsultationStatement> findByHospitalTypeOrderByIdAsc(String hospitalType);

    @Query("SELECT s FROM ConsultationStatement s WHERE (s.hospitalType = :hospitalType OR s.hospitalType = 'ALL') ORDER BY s.displayOrder ASC, s.id ASC")
    List<ConsultationStatement> findByHospitalTypeOrAllOrderByIdAsc(@Param("hospitalType") String hospitalType);

    List<ConsultationStatement> findByHospitalTypeAndCategoryOrderByIdAsc(String hospitalType, String category);

    @Query("SELECT s FROM ConsultationStatement s WHERE (s.hospitalType = :hospitalType OR s.hospitalType = 'ALL') " +
           "AND s.isActive = true ORDER BY s.displayOrder ASC, s.id ASC")
    List<ConsultationStatement> findActiveByHospitalTypeOrAll(@Param("hospitalType") String hospitalType);

    @Query("SELECT s FROM ConsultationStatement s WHERE (s.hospitalType = :hospitalType OR s.hospitalType = 'ALL') " +
           "AND s.category = :category AND s.isActive = true ORDER BY s.displayOrder ASC, s.id ASC")
    List<ConsultationStatement> findActiveByHospitalTypeOrAllAndCategory(
            @Param("hospitalType") String hospitalType,
            @Param("category") String category);

    @Query("SELECT s FROM ConsultationStatement s WHERE (s.hospitalType = :hospitalType OR s.hospitalType = 'ALL') " +
           "AND s.isActive = true AND (" +
           "LOWER(s.englishText) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.marathiText) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(s.hindiText) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "ORDER BY s.displayOrder ASC, s.id ASC")
    List<ConsultationStatement> searchActive(
            @Param("hospitalType") String hospitalType,
            @Param("query") String query);

    @Query("SELECT s FROM ConsultationStatement s WHERE (" +
           "LOWER(s.englishText) = LOWER(:englishText) OR " +
           "LOWER(s.englishText) = LOWER(CONCAT(:englishText, '.')) OR " +
           "CONCAT(LOWER(s.englishText), '.') = LOWER(:englishText)" +
           ") AND s.isActive = true")
    List<ConsultationStatement> findMatchingActiveStatements(@Param("englishText") String englishText);
}

