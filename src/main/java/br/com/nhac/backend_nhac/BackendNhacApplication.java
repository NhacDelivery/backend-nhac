package br.com.nhac.backend_nhac;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableAsync
// Necessário para o OfertaExpiracaoScheduler (V039). Sem isto o @Scheduled é
// simplesmente ignorado, sem erro nenhum no boot.
@org.springframework.scheduling.annotation.EnableScheduling
public class BackendNhacApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendNhacApplication.class, args);
	}

	@org.springframework.context.annotation.Bean
	public org.springframework.web.client.RestTemplate restTemplate() {
		var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(4000); factory.setReadTimeout(10000);
        return new org.springframework.web.client.RestTemplate(factory);
	}

	@jakarta.annotation.PostConstruct
public void init() {
    java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
}


}
