package com.seniorinsight.notification.config;

import java.time.Clock;
import java.time.Duration;

import com.seniorinsight.notification.adapter.out.http.CareRedeProtecaoClient;
import com.seniorinsight.notification.adapter.out.http.ExpoPushSender;
import com.seniorinsight.notification.adapter.out.mail.SmtpEmailSender;
import com.seniorinsight.notification.application.AvaliarAlertaUseCase;
import com.seniorinsight.notification.application.ConfirmarLeituraNotificacaoUseCase;
import com.seniorinsight.notification.application.EnviarSosPorPushUseCase;
import com.seniorinsight.notification.application.ListarAlertasUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.application.RegistrarTokenPushUseCase;
import com.seniorinsight.notification.application.RemoverTokenPushUseCase;
import com.seniorinsight.notification.application.ReenviarSosSemLeituraUseCase;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

@Configuration
@EnableScheduling
public class ApplicationConfig {

    private static final Duration TIMEOUT_CONEXAO = Duration.ofSeconds(3);
    private static final Duration TIMEOUT_LEITURA = Duration.ofSeconds(10);

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    ProcessarEventoSosUseCase processarEventoSosUseCase(NotificacaoRepository notificacaoRepository) {
        return new ProcessarEventoSosUseCase(notificacaoRepository);
    }

    @Bean
    RegistrarTokenPushUseCase registrarTokenPushUseCase(DispositivoPushRepository repository) {
        return new RegistrarTokenPushUseCase(repository);
    }

    @Bean
    ConfirmarLeituraNotificacaoUseCase confirmarLeituraNotificacaoUseCase(EntregaRepository entregas, Clock clock) {
        return new ConfirmarLeituraNotificacaoUseCase(entregas, clock);
    }

    @Bean
    RemoverTokenPushUseCase removerTokenPushUseCase(DispositivoPushRepository repository) {
        return new RemoverTokenPushUseCase(repository);
    }

    @Bean
    EnviarSosPorPushUseCase enviarSosPorPushUseCase(
            NotificacaoRepository notificacoes,
            RedeProtecaoClient rede,
            DispositivoPushRepository dispositivos,
            EntregaRepository entregas,
            PushSender pushSender,
            AlertaRepository alertas,
            Clock clock) {
        return new EnviarSosPorPushUseCase(notificacoes, rede, dispositivos, entregas, pushSender, alertas, clock);
    }

    @Bean
    ListarAlertasUseCase listarAlertasUseCase(AlertaRepository alertas, RedeProtecaoClient rede) {
        return new ListarAlertasUseCase(alertas, rede);
    }

    @Bean
    AvaliarAlertaUseCase avaliarAlertaUseCase(AlertaRepository alertas, RedeProtecaoClient rede, Clock clock) {
        return new AvaliarAlertaUseCase(alertas, rede, clock);
    }

    @Bean
    ReenviarSosSemLeituraUseCase reenviarSosSemLeituraUseCase(
            EntregaRepository entregas,
            NotificacaoRepository notificacoes,
            DispositivoPushRepository dispositivos,
            RedeProtecaoClient rede,
            PushSender pushSender,
            EmailSender emailSender,
            @Value("${notification.sos.prazo-leitura}") Duration prazoLeitura,
            Clock clock) {
        return new ReenviarSosSemLeituraUseCase(
                entregas, notificacoes, dispositivos, rede, pushSender, emailSender, prazoLeitura, clock);
    }

    @Bean
    RedeProtecaoClient redeProtecaoClient(
            @Value("${notification.care.base-url}") String baseUrl,
            @Value("${notification.care.rede-cache-ttl}") Duration ttlCache,
            Clock clock) {
        return new CareRedeProtecaoClient(restClient(baseUrl, null), ttlCache, clock);
    }

    @Bean
    PushSender pushSender(
            @Value("${notification.expo.url}") String url,
            @Value("${notification.expo.access-token}") String accessToken) {
        return new ExpoPushSender(restClient(url, accessToken));
    }

    @Bean
    EmailSender emailSender(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${notification.email.remetente}") String remetente) {
        return new SmtpEmailSender(mailSender, remetente);
    }

    private static RestClient restClient(String baseUrl, String bearer) {
        var fabrica = ClientHttpRequestFactoryBuilder.detect().build(
                ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(TIMEOUT_CONEXAO)
                        .withReadTimeout(TIMEOUT_LEITURA));
        RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl).requestFactory(fabrica);
        if (bearer != null && !bearer.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + bearer);
        }
        return builder.build();
    }
}
