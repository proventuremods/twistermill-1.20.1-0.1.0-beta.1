package com.proventure.twistermill.weather;

public record WindSample(
        boolean valid,
        float weather2WindSpeed,
        float windAngleDegrees,
        float rawBackendSpeed,
        String backendName,
        boolean rawPmweatherVectorPresent,
        double rawPmweatherVectorX,
        double rawPmweatherVectorY,
        double rawPmweatherVectorZ,
        double rawPmweatherLength,
        String invalidReason,
        String zeroReason,
        boolean heldPreviousPmwSample
) {
    public WindSample(boolean valid,
                      float weather2WindSpeed,
                      float windAngleDegrees,
                      float rawBackendSpeed,
                      String backendName) {
        this(valid, weather2WindSpeed, windAngleDegrees, rawBackendSpeed, backendName,
                false, 0.0D, 0.0D, 0.0D, 0.0D, "", "", false);
    }

    public static WindSample invalid(String backendName) {
        return invalid(backendName, "");
    }

    public static WindSample invalid(String backendName, String invalidReason) {
        return new WindSample(false, 0.0F, 0.0F, 0.0F, backendName,
                false, 0.0D, 0.0D, 0.0D, 0.0D, invalidReason, "", false);
    }

    public WindSample withPmwDiagnosticsFrom(WindSample diagnosticSource, boolean heldPreviousPmwSample) {
        if (diagnosticSource == null) {
            return new WindSample(valid, weather2WindSpeed, windAngleDegrees, rawBackendSpeed, backendName,
                    rawPmweatherVectorPresent, rawPmweatherVectorX, rawPmweatherVectorY, rawPmweatherVectorZ,
                    rawPmweatherLength, invalidReason, zeroReason, heldPreviousPmwSample);
        }

        return new WindSample(valid, weather2WindSpeed, windAngleDegrees, rawBackendSpeed, backendName,
                diagnosticSource.rawPmweatherVectorPresent(),
                diagnosticSource.rawPmweatherVectorX(),
                diagnosticSource.rawPmweatherVectorY(),
                diagnosticSource.rawPmweatherVectorZ(),
                diagnosticSource.rawPmweatherLength(),
                diagnosticSource.invalidReason(),
                diagnosticSource.zeroReason(),
                heldPreviousPmwSample);
    }
}
