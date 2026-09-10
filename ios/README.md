# Lumoos Medidor para iPhone — 0.2.0

Versión nativa SwiftUI/AVFoundation, iOS 16 o posterior. Comparte la lógica funcional de Android 0.2.0: LED amarillo, rojo, infrarrojo y cuadro oscuro del display; KH y vueltas editables; umbral manual; calibración de 3 segundos; histéresis y debounce de 12 ms; cronómetro por timestamps de cámara y cálculo de kW. La cámara y los botones quedan fuera del área desplazable. No se suben imágenes ni se requiere un servicio de IA.

La primera transición ausente→visible inicia el tiempo; cada transición posterior completa una vuelta. Si el indicador está visible al armar, espera primero su desaparición. Cambiar de modo reinicia la detección. Las interrupciones de cámara o salir de la app cancelan la medición para no contar periodos sin imágenes.

## Abrir y probar

En una Mac con Xcode y XcodeGen:

```sh
cd ios
swift test
xcodegen generate
open LumoosMedidor.xcodeproj
```

Seleccionar el target LumoosMedidor, elegir el equipo propio en Signing & Capabilities, conectar el iPhone y ejecutar. No se incluye una identidad de firma. Puede ser necesario cambiar el bundle identifier por uno disponible de la cuenta.

Para TestFlight: se requiere la cuenta Apple Developer correspondiente, firma y registro en App Store Connect. Después: Product → Archive → Distribute App. La compilación sin firma de CI NO se instala directamente en un iPhone y no es una entrega TestFlight.

## Verificación pendiente en dispositivo

La compilación y las pruebas del núcleo no validan precisión física. Comparar el modo LED y el cuadro superior del Elster A3RAL con conteo manual/video; verificar 1, 3, 5 y 10 ciclos; diferentes luces, enfoque y frecuencias de pulsos; permiso denegado, bloqueo del teléfono y regreso desde segundo plano. La detección infrarroja depende del filtro de cámara del iPhone. Calibrar de nuevo en cada modo. La KH debe corresponder al indicador medido, no asumir que todos los símbolos equivalen a la misma energía.

La referencia de imagen conserva el encuadre completo con barras negras si hace falta. El recuadro central corresponde al 10% y el exterior al 28% del cuadro de cámara. Mantener únicamente el indicador elegido dentro del centro.
