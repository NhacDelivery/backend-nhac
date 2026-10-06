package br.com.nhac.backend_nhac.exceptions;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ResourceExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ResourceExceptionHandler.class);

    @ExceptionHandler(NhacException.class)
    public ResponseEntity<ErroPadraoDTO> handleNhacException(NhacException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        logger.error("Erro Nhac: {}, RequestId: {}", e.getErrorCode(), requestId, e);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                e.getHttpStatus().value(),
                e.getErrorCode() != null ? e.getErrorCode().getCode() : "ERRO_DESCONHECIDO",
                getFriendlyTitle(e.getHttpStatus()),
                e.getMessage(),
                e.getDetails(),
                request.getRequestURI(),
                Collections.emptyList()
        );

        return ResponseEntity.status(e.getHttpStatus()).body(erro);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ErroPadraoDTO> acessoNegado(org.springframework.security.access.AccessDeniedException e, HttpServletRequest request) {
        return handleNhacException(new AcessoNegadoException("Você não tem permissão para realizar esta ação."), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErroPadraoDTO> regraDeNegocio(IllegalArgumentException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.warn("IllegalArgumentException: {}, RequestId: {}", e.getMessage(), requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.REGRA_DE_NEGOCIO.getCode(),
                "Violação de Regra de Negócio",
                e.getMessage(),
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.emptyList()
        );
        return ResponseEntity.status(status).body(erro);
    }

    @ExceptionHandler(org.springframework.dao.CannotAcquireLockException.class)
    public ResponseEntity<ErroPadraoDTO> handleCannotAcquireLockException(org.springframework.dao.CannotAcquireLockException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.CONFLICT;
        logger.warn("Conflito de concorrência (Deadlock): {}, RequestId: {}", e.getMessage(), requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.ERRO_INTERNO_SERVIDOR.getCode(),
                "Conflito de Concorrência",
                "Muitas requisições simultâneas. Por favor, tente novamente.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.emptyList()
        );
        return ResponseEntity.status(status).body(erro);
    }

    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.core.convert.ConversionFailedException.class
    })
    public ResponseEntity<ErroPadraoDTO> parametroInvalido(Exception e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.warn("Erro de conversão de parâmetro: {}, RequestId: {}", e.getMessage(), requestId);

        String mensagem = "Parâmetro inválido. Verifique o valor enviado.";
        if (e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException mismatch) {
            Class<?> tipo = mismatch.getRequiredType();
            if (tipo != null && tipo.isEnum()) {
                mensagem = "Valor inválido para o parâmetro '" + mismatch.getName()
                        + "'. Valores aceitos: " + java.util.Arrays.toString(tipo.getEnumConstants());
            } else {
                mensagem = "Valor inválido para o parâmetro '" + mismatch.getName() + "'.";
            }
        }

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.VALIDACAO_FALHOU.getCode(),
                "Requisição Inválida",
                mensagem,
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.singletonList("Verifique os parâmetros da URL e tente novamente.")
        );
        return ResponseEntity.status(status).body(erro);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroPadraoDTO> validacaoDeCampos(MethodArgumentNotValidException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.warn("Validation Error, RequestId: {}", requestId);

        String mensagensValidacao = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));

        Map<String, Object> details = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                    fieldError -> fieldError.getField(),
                    fieldError -> fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "Campo inválido",
                    (msg1, msg2) -> msg1 + "; " + msg2
                ));

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.VALIDACAO_FALHOU.getCode(),
                "Erro de Validação de Dados",
                "Alguns campos enviados são inválidos. Verifique os detalhes.",
                details,
                request.getRequestURI(),
                Collections.singletonList("Verifique os campos informados e tente novamente.")
        );
        return ResponseEntity.status(status).body(erro);
    }

    // ============================================================
    //  HANDLERS NOVOS — resolvem os 500 dos webhooks e uploads
    // ============================================================

    /**
     * Corpo da requisição ausente, vazio ou malformado (JSON inválido).
     * Sem este handler, o Spring lança HttpMessageNotReadableException
     * e o @ExceptionHandler(Exception.class) genérico abaixo transforma
     * em 500, sobrescrevendo o 400 que seria o correto.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroPadraoDTO> corpoInvalido(HttpMessageNotReadableException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.warn("Corpo da requisição inválido/ausente: {}, RequestId: {}", e.getMessage(), requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.VALIDACAO_FALHOU.getCode(),
                "Requisição Inválida",
                "Corpo da requisição ausente ou em formato inválido.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.singletonList("Envie um JSON válido no corpo da requisição.")
        );
        return ResponseEntity.status(status).body(erro);
    }

    /**
     * Parâmetro obrigatório ausente — cobre @RequestParam e @RequestPart
     * (upload de arquivo sem o campo "file" cai aqui).
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ErroPadraoDTO> parametroAusente(Exception e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.warn("Parâmetro obrigatório ausente: {}, RequestId: {}", e.getMessage(), requestId);

        String mensagem = "Parâmetro obrigatório ausente.";
        if (e instanceof MissingServletRequestParameterException missing) {
            mensagem = "Parâmetro obrigatório ausente: '" + missing.getParameterName() + "'.";
        } else if (e instanceof MissingServletRequestPartException missingPart) {
            mensagem = "Arquivo obrigatório ausente: '" + missingPart.getRequestPartName() + "'.";
        }

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.VALIDACAO_FALHOU.getCode(),
                "Requisição Inválida",
                mensagem,
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.singletonList("Verifique os parâmetros obrigatórios e tente novamente.")
        );
        return ResponseEntity.status(status).body(erro);
    }

    /**
     * Content-Type não suportado (ex.: enviar text/plain pra um endpoint
     * que espera application/json ou multipart/form-data).
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErroPadraoDTO> tipoMidiaNaoSuportado(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
        logger.warn("Content-Type não suportado: {}, RequestId: {}", e.getContentType(), requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.VALIDACAO_FALHOU.getCode(),
                "Tipo de Conteúdo Não Suportado",
                "O Content-Type enviado não é suportado por esta rota.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.singletonList("Content-Type aceito: " + e.getSupportedMediaTypes())
        );
        return ResponseEntity.status(status).body(erro);
    }

    /**
     * Método HTTP não suportado (ex.: DELETE onde só aceita GET).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroPadraoDTO> metodoNaoSuportado(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.METHOD_NOT_ALLOWED;
        logger.warn("Método não suportado: {} em {}, RequestId: {}", e.getMethod(), request.getRequestURI(), requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.VALIDACAO_FALHOU.getCode(),
                "Método Não Permitido",
                "O método HTTP " + e.getMethod() + " não é suportado nesta rota.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.singletonList("Métodos aceitos: " + e.getSupportedHttpMethods())
        );
        return ResponseEntity.status(status).body(erro);
    }

    // ============================================================
    //  HANDLERS PRÉ-EXISTENTES
    // ============================================================

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErroPadraoDTO> noResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.NOT_FOUND;
        logger.warn("Rota não encontrada: {}, RequestId: {}", request.getRequestURI(), requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                "ROTA_NAO_ENCONTRADA",
                "Rota Não Encontrada",
                "A rota solicitada não existe no servidor.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.singletonList("Verifique a URL e o método HTTP solicitados.")
        );
        return ResponseEntity.status(status).body(erro);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroPadraoDTO> arquivoMuitoGrande(org.springframework.web.multipart.MaxUploadSizeExceededException e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.BAD_REQUEST;
        logger.warn("Upload acima do limite permitido, RequestId: {}", requestId);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.REGRA_DE_NEGOCIO.getCode(),
                "Arquivo Muito Grande",
                "O arquivo enviado é maior que o limite permitido.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.emptyList()
        );
        return ResponseEntity.status(status).body(erro);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroPadraoDTO> erroGenerico(Exception e, HttpServletRequest request) {
        String requestId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        logger.error("Erro Interno não tratado. RequestId: {}", requestId, e);

        ErroPadraoDTO erro = new ErroPadraoDTO(
                requestId,
                Instant.now(),
                status.value(),
                ErrorCode.ERRO_INTERNO_SERVIDOR.getCode(),
                "Erro Interno do Servidor",
                "Ocorreu um erro inesperado no servidor. Por favor, tente novamente mais tarde.",
                Collections.emptyMap(),
                request.getRequestURI(),
                Collections.emptyList()
        );
        return ResponseEntity.status(status).body(erro);
    }

    private String getFriendlyTitle(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Recurso Não Encontrado";
            case BAD_REQUEST -> "Requisição Inválida";
            case UNAUTHORIZED -> "Não Autorizado";
            case FORBIDDEN -> "Acesso Negado";
            case UNPROCESSABLE_ENTITY -> "Entidade Não Processável";
            case TOO_MANY_REQUESTS -> "Muitas Requisições";
            case PAYMENT_REQUIRED -> "Pagamento Recusado";
            case CONFLICT -> "Conflito";
            default -> "Erro na Requisição";
        };
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
public ResponseEntity<ErroPadraoDTO> dataIntegrity(DataIntegrityViolationException e, HttpServletRequest request) {
    String requestId = UUID.randomUUID().toString();
    HttpStatus status = HttpStatus.CONFLICT;
    logger.warn("Conflito de integridade de dados: {}, RequestId: {}", e.getMostSpecificCause().getMessage(), requestId);

    ErroPadraoDTO erro = new ErroPadraoDTO(
            requestId,
            Instant.now(),
            status.value(),
            ErrorCode.REGRA_DE_NEGOCIO.getCode(),
            "Conflito de Dados",
            "A operação viola uma restrição de integridade dos dados.",
            Collections.emptyMap(),
            request.getRequestURI(),
            Collections.emptyList()
    );
    return ResponseEntity.status(status).body(erro);
}
}