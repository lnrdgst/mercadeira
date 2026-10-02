package com.mercadeira.api.autenticacao.email;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Service
@ConditionalOnProperty(name = "mercadeira.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailService implements EmailService {
    private final JavaMailSender mailSender;
    private final String remetente;
    public SmtpEmailService(JavaMailSender mailSender, @Value("${mercadeira.mail.from:}") String remetente) {
        this.mailSender = mailSender; this.remetente = remetente;
    }
    @Override public void enviarRedefinicaoSenha(String destinatario, String link, Duration validade) {
        if (remetente.isBlank()) throw new IllegalStateException("MAIL_FROM deve ser configurado para envio de e-mail.");
        try {
            MimeMessageHelper mensagem = new MimeMessageHelper(mailSender.createMimeMessage(), false,
                    StandardCharsets.UTF_8.name());
            mensagem.setFrom(remetente); mensagem.setTo(destinatario);
            mensagem.setSubject("Redefini\u00e7\u00e3o de senha \u2014 Mercadeira");
            mensagem.setText("Foi solicitada uma redefinicao de senha para sua conta.\n\n"
                    + "Redefinir senha: " + link + "\n\nEste link e valido por " + validade.toMinutes()
                    + " minutos e pode ser usado uma unica vez. Se voce nao fez esta solicitacao, ignore este e-mail.");
            mailSender.send(mensagem.getMimeMessage());
        } catch (MessagingException exception) {
            throw new MailPreparationException("Nao foi possivel preparar o e-mail de redefinicao de senha.", exception);
        }
    }
}
