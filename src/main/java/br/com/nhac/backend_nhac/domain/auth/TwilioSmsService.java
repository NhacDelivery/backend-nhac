package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.exceptions.ServicoIndisponivelException;
import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class TwilioSmsService implements SmsService {

    private static final Logger logger = LoggerFactory.getLogger(TwilioSmsService.class);
    private static final String SMS_INDISPONIVEL =
            "O envio de SMS está indisponível agora. Tente novamente ou entre com e-mail.";

    @Value("${twilio.account-sid:}")
    private String accountSid;

    @Value("${twilio.auth-token:}")
    private String authToken;

    @Value("${twilio.from-number:}")
    private String fromNumber;

    @Value("${twilio.messaging-service-sid:}")
    private String messagingServiceSid;

    @Value("${nhac.sms.mock-mode:true}")
    private boolean mockMode;

    @PostConstruct
    public void init() {
        if (mockMode) {
            logger.info("Twilio SMS operando em modo MOCK / Log no terminal.");
        } else if (configurado()) {
            Twilio.init(accountSid, authToken);
        } else {
            logger.warn("Envio de SMS indisponível: configuração do Twilio incompleta.");
        }
    }

    private boolean configurado() {
        return StringUtils.hasText(accountSid) && StringUtils.hasText(authToken) && (StringUtils.hasText(fromNumber) || StringUtils.hasText(messagingServiceSid));
    }

    @Override
    public void enviarSms(String telefoneDestino, String mensagem) {
        if (mockMode) {
            logger.info("=================================================");
            logger.info("[SMS MOCK - NHAC DELIVERY]");
            logger.info("Destinatário: {}", telefoneDestino);
            logger.info("Mensagem: {}", mensagem);
            logger.info("=================================================");
            return;
        }

        if (!configurado()) {
            throw new ServicoIndisponivelException(SMS_INDISPONIVEL);
        }
        try {
            PhoneNumber destino = new PhoneNumber(telefoneDestino);
            var envio = StringUtils.hasText(messagingServiceSid)
                    ? Message.creator(destino, messagingServiceSid.trim(), mensagem)
                    : Message.creator(destino, new PhoneNumber(fromNumber.trim()), mensagem);
            envio.create();
        } catch (ApiException e) {
            logger.error("Twilio recusou o envio de SMS. Código do provedor: {}", e.getCode());
            throw new ServicoIndisponivelException(SMS_INDISPONIVEL);
        } catch (RuntimeException e) {
            logger.error("Falha ao enviar SMS pelo Twilio ({})", e.getClass().getSimpleName());
            throw new ServicoIndisponivelException(SMS_INDISPONIVEL);
        }
    }
}
