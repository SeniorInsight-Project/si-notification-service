package com.seniorinsight.notification.config;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;

import com.seniorinsight.notification.adapter.out.http.CareRedeProtecaoClient;
import com.seniorinsight.notification.adapter.out.http.ExpoPushSender;
import com.seniorinsight.notification.adapter.out.mail.SmtpEmailSender;
import com.seniorinsight.notification.application.AvaliarAlertaUseCase;
import com.seniorinsight.notification.application.ConfirmarLeituraNotificacaoUseCase;
import com.seniorinsight.notification.application.EnviarSosPorPushUseCase;
import com.seniorinsight.notification.application.AvisarRedeExclusaoUseCase;
import com.seniorinsight.notification.application.ExcluirTitularUseCase;
import com.seniorinsight.notification.application.ListarAlertasUseCase;
import com.seniorinsight.notification.application.Pausa;
import com.seniorinsight.notification.application.ProcessarEventoSosUseCase;
import com.seniorinsight.notification.application.ProcessarEventoSinalUseCase;
import com.seniorinsight.notification.application.RegistrarTokenPushUseCase;
import com.seniorinsight.notification.application.RemoverTokenPushUseCase;
import com.seniorinsight.notification.application.ReenviarSosSemLeituraUseCase;
import com.seniorinsight.notification.application.Retentativa;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EmailSender;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.ProtocoloExclusaoRepository;
import com.seniorinsight.notification.domain.port.PublicadorExclusaoConcluida;
import com.seniorinsight.notification.domain.port.PushSender;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import com.seniorinsight.notification.domain.port.TitularExcluidoRepository;
import com.seniorinsight.notification.domain.port.Transacao;
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
    ProcessarEventoSinalUseCase processarEventoSinalUseCase(
            NotificacaoRepository notificacoes,
            RedeProtecaoClient rede,
            DispositivoPushRepository dispositivos,
            EntregaRepository entregas,
            PushSender pushSender,
            EmailSender emailSender,
            AlertaRepository alertas,
            Clock clock,
            @Value("${notification.sinal.fuso-horario}") ZoneId fusoHorario) {
        return new ProcessarEventoSinalUseCase(
                notificacoes, rede, dispositivos, entregas, pushSender, emailSender, alertas, clock, fusoHorario);
    }

    @Bean
    RegistrarTokenPushUseCase registrarTokenPushUseCase(
            DispositivoPushRepository repository, TitularExcluidoRepository titulares, Transacao transacao) {
        return new RegistrarTokenPushUseCase(repository, titulares, transacao);
    }

    @Bean
    AvisarRedeExclusaoUseCase avisarRedeExclusaoUseCase(
            RedeProtecaoClient rede,
            DispositivoPushRepository dispositivos,
            PushSender pushSender,
            EmailSender emailSender,
            Pausa pausa,
            @Value("${notification.exclusao.aviso-rede.tentativas}") int tentativas,
            @Value("${notification.exclusao.aviso-rede.intervalo}") Duration intervalo) {
        return new AvisarRedeExclusaoUseCase(
                rede, dispositivos, pushSender, emailSender, new Retentativa(tentativas, intervalo, pausa));
    }

    @Bean
    ExcluirTitularUseCase excluirTitularUseCase(
            ProtocoloExclusaoRepository protocolos,
            TitularExcluidoRepository titulares,
            AvisarRedeExclusaoUseCase avisarRede,
            Transacao transacao,
            DispositivoPushRepository dispositivos,
            EntregaRepository entregas,
            NotificacaoRepository notificacoes,
            AlertaRepository alertas,
            PublicadorExclusaoConcluida publicador) {
        return new ExcluirTitularUseCase(
                protocolos, titulares, avisarRede, transacao, dispositivos, entregas, notificacoes, alertas, publicador);
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

    /** Pausa a thread do consumidor entre as tentativas; interrompida, restaura o sinal e segue. */
    @Bean
    Pausa pausa() {
        return intervalo -> {
            try {
                Thread.sleep(intervalo);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        };
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
