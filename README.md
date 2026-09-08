# Lumoos Medidor LED — Android V1

Aplicación Android nativa para calcular potencia a partir de los parpadeos del LED de un medidor.

## Fórmula implementada

`kW = (revoluciones × KH × 3.6) / tiempo_segundos`

Una revolución se define como:

`ENCENDIDO → APAGADO → ENCENDIDO`

La app espera un flanco limpio `APAGADO → ENCENDIDO` para iniciar el cronómetro. Cada siguiente flanco `APAGADO → ENCENDIDO` completa una revolución.

## Funciones incluidas

- Cámara trasera con CameraX.
- Recuadro central para apuntar al LED.
- Análisis de luminosidad local, sin subir imágenes a Internet.
- KH editable.
- 1, 3, 5, 10 o número personalizado de vueltas.
- Cronómetro automático.
- Conteo automático de vueltas.
- Umbral de sensibilidad manual.
- Calibración automática de 3 segundos.
- Histéresis y debounce para reducir falsos pulsos.
- Resultado de kW y visualización de la fórmula usada.

## Versiones base

- compileSdk / targetSdk: 37
- Android Gradle Plugin: 9.4.0
- Kotlin: 2.3.21
- Compose BOM: 2026.08.00
- CameraX: 1.6.2
- minSdk: 23

## Abrir en Android Studio

1. Abre la carpeta `LumoosMedidorLED` en Android Studio.
2. Usa Android Studio Quail 4 (2026.1.4) o una versión compatible con AGP 9.4.
3. Configura Gradle 9.6 si Android Studio te lo solicita.
4. Instala Android SDK API 37.
5. Ejecuta en un teléfono Android físico y concede permiso de cámara.

> Esta entrega contiene el proyecto fuente. El entorno usado para generarlo no incluye Android SDK/Gradle, por lo que aquí no se generó un APK firmado.

## Próximas mejoras recomendadas

- Guardar historial de mediciones.
- Perfiles de medidor con KH preguardada.
- Selector de tamaño del área de detección.
- Bloqueo de exposición/enfoque para lecturas más estables.
- Señal sonora/vibración por cada vuelta detectada.
- Exportación a PDF/CSV.
