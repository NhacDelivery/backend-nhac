package br.com.nhac.backend_nhac.domain.auth;

import br.com.nhac.backend_nhac.exceptions.ServicoIndisponivelException;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.rest.api.v2010.account.MessageCreator;
import com.twilio.type.PhoneNumber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwilioSmsServiceTest {

    @InjectMocks
    private TwilioSmsService twilioSmsService;

    @BeforeEach
    void setUp() {
        // Defaults to mock mode for safety
        ReflectionTestUtils.setField(twilioSmsService, "mockMode", true);
        ReflectionTestUtils.setField(twilioSmsService, "accountSid", "mockSid");
        ReflectionTestUtils.setField(twilioSmsService, "authToken", "mockToken");
        ReflectionTestUtils.setField(twilioSmsService, "fromNumber", "+1234567890");
    }

    @Test
    void init_ComMockModeTrue_DeveOperarEmMock() {
        assertDoesNotThrow(() -> twilioSmsService.init());
    }

    @Test
    void init_ComMockModeFalse_InicializaTwilio() {
        ReflectionTestUtils.setField(twilioSmsService, "mockMode", false);
        assertDoesNotThrow(() -> twilioSmsService.init());
    }

    @Test
    void enviarSms_EmMockMode_ApenasLoga() {
        assertDoesNotThrow(() -> twilioSmsService.enviarSms("+5511999999999", "Teste de mensagem mock"));
    }

    @Test
    void enviarSms_RealComConfiguracaoIncompleta_FalhaSemSimularEnvio() {
        ReflectionTestUtils.setField(twilioSmsService, "mockMode", false);
        ReflectionTestUtils.setField(twilioSmsService, "accountSid", "");
        assertThrows(ServicoIndisponivelException.class,
                () -> twilioSmsService.enviarSms("+5511999999999", "Código de teste"));
    }

    @Test
    void aceitaMessagingServiceSemNumeroRemetente() {
        ReflectionTestUtils.setField(twilioSmsService, "mockMode", false);
        ReflectionTestUtils.setField(twilioSmsService, "fromNumber", "");
        ReflectionTestUtils.setField(twilioSmsService, "messagingServiceSid", "MGteste");
        MessageCreator envio = mock(MessageCreator.class);
        try (var messages = mockStatic(Message.class)) {
            messages.when(() -> Message.creator(new PhoneNumber("+5511999999999"), "MGteste", "teste")).thenReturn(envio);
            twilioSmsService.enviarSms("+5511999999999", "teste");
            org.mockito.Mockito.verify(envio).create();
        }
    }

    @Test
    void enviarSms_RealComFalhaDoProvedor_RetornaServicoIndisponivel() {
        ReflectionTestUtils.setField(twilioSmsService, "mockMode", false);
        String destino = "+5511999999999";
        String mensagem = "Código de teste";
        MessageCreator envio = mock(MessageCreator.class);
        when(envio.create()).thenThrow(new ApiException("Falha simulada", 21408));

        try (var messages = mockStatic(Message.class)) {
            messages.when(() -> Message.creator(new PhoneNumber(destino), new PhoneNumber("+1234567890"), mensagem))
                    .thenReturn(envio);
            assertThrows(ServicoIndisponivelException.class, () -> twilioSmsService.enviarSms(destino, mensagem));
        }
    }
}
