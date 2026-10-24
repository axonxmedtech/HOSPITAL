package com.hms.integration;

import com.hms.entity.Patient;
import com.hms.entity.Billing;
import com.hms.repository.PatientRepository;
import com.hms.service.hospital.PatientRegistrar;
import com.hms.service.hospital.TenantCustomIdAllocator;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers
class TenantCustomIdConcurrencyIT {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("synthetic_release_ids").withUsername("test").withPassword("test");
    SessionFactory factory;
    @BeforeEach void setup() {
        factory = new Configuration().addAnnotatedClass(Patient.class).addAnnotatedClass(Billing.class)
            .setProperty("hibernate.connection.url", MYSQL.getJdbcUrl())
            .setProperty("hibernate.connection.username", MYSQL.getUsername())
            .setProperty("hibernate.connection.password", MYSQL.getPassword())
            .setProperty("hibernate.hbm2ddl.auto", "create-drop")
            .setProperty("jakarta.persistence.validation.mode", "none").buildSessionFactory();
        try (EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();
            em.createNativeQuery("CREATE TABLE IF NOT EXISTS hospitals(id BIGINT PRIMARY KEY)").executeUpdate();
            em.createNativeQuery("INSERT IGNORE INTO hospitals VALUES(1),(2)").executeUpdate();
            em.getTransaction().commit();
        }
    }
    @AfterEach void close() { if(factory!=null) factory.close(); }
    TenantCustomIdAllocator allocator(EntityManager em) {
        var a=new TenantCustomIdAllocator(); ReflectionTestUtils.setField(a,"entityManager",em); return a;
    }
    Patient patient(String custom) {
        Patient p=new Patient();p.setHospitalId(1L);p.setName("SYNTHETIC");p.setPhone("1234567890");
        p.setGender("OTHER");p.setCustomId(custom);return p;
    }
    PatientRegistrar registrar(EntityManager em) {
        PatientRepository repo=mock(PatientRepository.class);
        when(repo.saveAndFlush(any(Patient.class))).thenAnswer(a->{Patient p=a.getArgument(0);em.persist(p);em.flush();return p;});
        doAnswer(a->{em.flush();return null;}).when(repo).flush();
        var registrar=new PatientRegistrar();ReflectionTestUtils.setField(registrar,"patientRepository",repo);
        ReflectionTestUtils.setField(registrar,"customIdAllocator",allocator(em));return registrar;
    }
    Billing bill(String custom) {
        Billing b=new Billing();b.setHospitalId(1L);b.setPatientId(1L);b.setDoctorId(1L);
        b.setAmount(BigDecimal.ONE);b.setBillingType("OPD");b.setPaymentStatus("PENDING");b.setCustomId(custom);return b;
    }
    void parallel(boolean billing) throws Exception {
        var pool=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);
        try {
            List<Future<String>> results=new ArrayList<>();
            for(int i=0;i<8;i++) results.add(pool.submit(()->{
                start.await();
                try(EntityManager em=factory.createEntityManager()) {
                    em.getTransaction().begin();
                    // Establish a repeatable-read snapshot before waiting on the shared tenant lock.
                    em.createQuery("SELECT COUNT(p) FROM Patient p",Long.class).getSingleResult();
                    String id;
                    if(billing) {
                        id=allocator(em).allocateBilling(1L);em.persist(bill(id));
                    } else id=registrar(em).persistNewPatient(patient(null)).getCustomId();
                    Thread.sleep(20);em.getTransaction().commit();return id;
                }
            }));
            start.countDown();Set<String> ids=new HashSet<>();
            for(var f:results) assertThat(ids.add(f.get(30,TimeUnit.SECONDS))).isTrue();
            assertThat(ids).hasSize(8);
        } finally {pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void patientAllocationIsSerializedAcrossConnections() throws Exception {parallel(false);}
    @Test void billingAllocationIsSerializedAcrossConnections() throws Exception {parallel(true);}
    @Test void assignedIdsPreservedAndLegacyCollisionProbed() {
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();var registrar=registrar(em);
            assertThat(registrar.persistNewPatient(patient("PAT2")).getCustomId()).isEqualTo("PAT2");
            assertThat(registrar.persistNewPatient(patient(null)).getCustomId()).isEqualTo("PAT3");
            em.getTransaction().commit();
        }
    }
    @Test void billingCollisionProbedAndTenantScopePreserved() {
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();em.persist(bill("BIL1001"));em.persist(bill("ARBITRARY"));
            assertThat(allocator(em).allocateBilling(1L)).isEqualTo("BIL1002");
            assertThat(allocator(em).allocateBilling(2L)).isEqualTo("BIL1001");em.getTransaction().rollback();
        }
    }
    @Test void failureRollsBackBothNewRecordsAndRetainsAssignedNumber() {
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();em.persist(bill("legacy"));em.getTransaction().commit();
            em.getTransaction().begin();registrar(em).persistNewPatient(patient(null));
            assertThatThrownBy(()->{em.persist(bill("legacy"));em.flush();}).isInstanceOf(RuntimeException.class);
            em.getTransaction().rollback();
        }
        try(EntityManager em=factory.createEntityManager()) {
            assertThat(em.createQuery("SELECT COUNT(p) FROM Patient p",Long.class).getSingleResult()).isZero();
            assertThat(em.createQuery("SELECT b.customId FROM Billing b",String.class).getSingleResult()).isEqualTo("legacy");
        }
    }
    @Test void finiteProbeLimitAndOverflowFailClosed() {
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();for(int i=1;i<=1000;i++) em.persist(patient("PAT"+i));em.flush();
            assertThatThrownBy(()->allocator(em).allocate("Patient",1L,1L)).isInstanceOf(IllegalStateException.class);
            Patient max=patient("PAT"+Long.MAX_VALUE);em.persist(max);em.flush();
            assertThatThrownBy(()->allocator(em).allocate("Patient",1L,Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);
            em.getTransaction().rollback();
        }
    }
    @Test void idsOneAnd9001NeverWrap() {
        try(EntityManager em=factory.createEntityManager()) {
            em.getTransaction().begin();assertThat(allocator(em).allocate("Patient",1L,1L)).isEqualTo("PAT1");
            assertThat(allocator(em).allocate("Patient",1L,9001L)).isEqualTo("PAT9001");em.getTransaction().rollback();
        }
    }
}
