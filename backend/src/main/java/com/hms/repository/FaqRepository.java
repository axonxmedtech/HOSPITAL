package com.hms.repository;

import com.hms.entity.Faq;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import java.util.List;

@Repository
public interface FaqRepository extends JpaRepository<Faq, Long> {

    @Cacheable(value = "faqs")
    List<Faq> findAll();

    @Override
    @CacheEvict(value = "faqs", allEntries = true)
    <S extends Faq> S save(S entity);

    @Override
    @CacheEvict(value = "faqs", allEntries = true)
    void deleteById(Long id);
}
