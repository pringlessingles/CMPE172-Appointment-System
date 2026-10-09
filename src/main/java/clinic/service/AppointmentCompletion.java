package clinic.service;

import clinic.repository.BookingRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AppointmentCompletion {
    private final BookingRepository repository;
    public AppointmentCompletion(BookingRepository repository) { this.repository = repository; }
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void completePastAppointments() { repository.completePastAppointments(); }
}
