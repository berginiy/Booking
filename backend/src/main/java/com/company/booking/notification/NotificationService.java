package com.company.booking.notification;

import com.company.booking.config.AppProperties;
import com.company.booking.dto.Dtos.MailStatus;
import com.company.booking.exception.ApiException;
import com.company.booking.security.CurrentUser;
import com.company.booking.service.AuditService;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final AppProperties props;
    private final AuditService audit;
    private final String host;
    private final int port;

    public NotificationService(ObjectProvider<JavaMailSender> senderProvider, AppProperties props, AuditService audit,
                               @Value("${spring.mail.host:}") String host,
                               @Value("${spring.mail.port:0}") int port) {
        this.senderProvider = senderProvider;
        this.props = props;
        this.audit = audit;
        this.host = host;
        this.port = port;
    }

    public boolean isEnabled() {
        return props.mail() != null && props.mail().enabled();
    }

    public MailStatus status() {
        AppProperties.Mail m = props.mail();
        return new MailStatus(isEnabled(), m == null ? "" : m.from(), host, port, m == null ? "" : m.siteUrl());
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingNotification(BookingNotification n) {
        if (!isEnabled()) return;
        BookingSnapshot b = n.booking();
        MailContentBuilder.Content content = MailContentBuilder.build(n.kind(), b, props.mail().siteUrl());
        boolean withCalendar = n.kind() == BookingNotification.Kind.INVITED || n.kind() == BookingNotification.Kind.UPDATED;
        byte[] ics = withCalendar ? MailContentBuilder.ics(b, domainOf(props.mail().from())) : null;

        for (Person p : n.recipients()) {
            try {
                send(p.email(), p.fullName(), content, ics);
                audit.log("system", "EMAIL_SEND", "BOOKING", b.id(), "SUCCESS", n.kind() + ": " + p.email());
            } catch (Exception e) {
                log.warn("Не удалось отправить письмо {}: {}", p.email(), e.getMessage());
                audit.log("system", "EMAIL_SEND", "BOOKING", b.id(), "FAILED",
                        n.kind() + ": " + p.email() + " - " + rootMessage(e));
            }
        }
    }

    public void sendTest(String to) {
        if (!isEnabled()) {
            throw ApiException.badRequest("MAIL_DISABLED", "Почта отключена: задайте MAIL_ENABLED=true и перезапустите сервер");
        }
        MailContentBuilder.Content content = new MailContentBuilder.Content(
                "Тестовое письмо: система бронирования залов",
                "Если вы читаете это письмо, отправка почты настроена верно.",
                "<p style=\"font-family:'Segoe UI',Arial,sans-serif\">Если вы читаете это письмо, отправка почты настроена верно.</p>");
        try {
            send(to, to, content, null);
            audit.log(CurrentUser.adminName(), "MAIL_TEST", "MAIL", null, "SUCCESS", to);
        } catch (Exception e) {
            audit.log(CurrentUser.adminName(), "MAIL_TEST", "MAIL", null, "FAILED", to + " - " + rootMessage(e));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "MAIL_ERROR", "Не удалось отправить письмо: " + rootMessage(e));
        }
    }

    private void send(String to, String toName, MailContentBuilder.Content c, byte[] ics) throws Exception {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null) throw new IllegalStateException("Почтовый клиент не настроен (spring.mail.host)");
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(new InternetAddress(props.mail().from(), props.mail().fromName(), "UTF-8"));
        helper.setTo(new InternetAddress(to, toName, "UTF-8"));
        helper.setSubject(c.subject());
        helper.setText(c.text(), c.html());
        if (ics != null) {
            helper.addAttachment("meeting.ics", new ByteArrayResource(ics), "text/calendar; charset=UTF-8");
        }
        sender.send(message);
    }

    private static String domainOf(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        return at < 0 ? "booking.local" : email.substring(at + 1);
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
