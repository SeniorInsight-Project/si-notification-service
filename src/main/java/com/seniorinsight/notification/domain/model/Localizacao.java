package com.seniorinsight.notification.domain.model;

import java.util.Optional;

public record Localizacao(double latitude, double longitude) {

    public Localizacao {
        if (!coordenadasValidas(latitude, longitude)) {
            throw new IllegalArgumentException(
                    "Coordenadas inválidas: latitude=" + latitude + ", longitude=" + longitude + ".");
        }
    }

    /**
     * Cria a localização só quando as duas coordenadas estão presentes e dentro dos limites.
     * Uma localização incompleta ou inválida não deve impedir o registro de um SOS.
     */
    public static Optional<Localizacao> seValida(Double latitude, Double longitude) {
        if (latitude == null || longitude == null || !coordenadasValidas(latitude, longitude)) {
            return Optional.empty();
        }
        return Optional.of(new Localizacao(latitude, longitude));
    }

    private static boolean coordenadasValidas(double latitude, double longitude) {
        return latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }
}
