# 📖 ReAppzuku — Preguntas frecuentes (FAQ)

> Guía completa para configurar y usar ReAppzuku

---

## Tabla de contenidos

- [¿Qué es ReAppzuku?](#qué-es-reappzuku)
- [Requisitos](#requisitos)
- [Configuración de supervivencia en segundo plano](#configuración-de-supervivencia-en-segundo-plano)
- [¿Por dónde empezar?](#por-dónde-empezar)
- [Control manual](#control-manual)
- [Principal](#principal)
  - [Barra de herramientas](#barra-de-herramientas)
  - [Disparadores de apps (App Triggers)](#disparadores-de-apps-app-triggers)
- [Ajustes](#ajustes)
  - [Información](#-información)
  - [Apariencia](#-apariencia)
  - [Estabilidad de la app](#️-estabilidad-de-la-app)
  - [Ajustes de Auto-Kill](#-ajustes-de-auto-kill)
  - [Herramientas avanzadas](#-herramientas-avanzadas)
    - [Restricciones de segundo plano](#restricciones-de-segundo-plano)
    - [Programador de restricciones](#programador-de-restricciones)
    - [Modo sueño](#modo-sueño-sleep-mode)
  - [Acerca de](#ℹ️-acerca-de)
- [Estadísticas y Registros](#-estadísticas-y-registros)
- [Apps protegidas](#apps-protegidas)
- [Preguntas frecuentes (FAQ)](#preguntas-frecuentes-faq)

---

## ¿Qué es ReAppzuku?

**ReAppzuku** es una utilidad para el diagnóstico y la gestión de procesos en segundo plano. Ofrece una amplia selección de escenarios de restricción para cada app.

`¿Por qué es necesario ReAppzuku si el Android moderno ya gestiona bien el control de apps por sí solo?` — sí, lo hace, pero no a la perfección. Los desarrolladores del SO mejoran y modernizan activamente los mecanismos del sistema para la gestión de procesos. Mientras tanto, numerosos vacíos legales permiten que las apps permanezcan activas en segundo plano. Estos van desde receptores (receivers) inofensivos hasta alarmas agresivas, Wakelocks y otros mecanismos de retención. En última instancia, impiden que los dispositivos entren en el modo de suspensión profunda, sobrecargan la CPU/RAM y agotan con gusto la energía de la batería.

---

## Requisitos
↩️[Tabla de contenidos](#table-of-contents)

| Requisito | Descripción |
|---|---|
| **Android** | 6.0 o superior. Las restricciones de segundo plano solo están disponibles en Android 11+ |
| **Root** o **Shizuku** | Se requiere uno de los dos |

### Root vs Shizuku

- **Root** — modo preferido, se usa automáticamente si está disponible
- **Shizuku** — alternativa sin root. Se instala desde Play Store, requiere una configuración inicial mediante ADB o el modo de desarrollador de MIUI/HyperOS

> [!NOTE]
> El modo de funcionamiento actual siempre se muestra en **Ajustes → Información → Modo de funcionamiento**

---

## Configuración de supervivencia en segundo plano
↩️[Tabla de contenidos](#table-of-contents)

Para que ReAppzuku se ejecute de forma confiable sin ser cerrado por el sistema, configura correctamente los permisos. Los pasos dependen de tu firmware.

---

### Optimización de batería (todos los firmwares)

El paso más importante. Si no se deshabilita, el sistema cerrará periódicamente a ReAppzuku.

**Ajustes → Apps → ReAppzuku → Batería → Sin restricciones**

O mediante el diálogo del sistema:

**Ajustes → Batería → Optimización de batería → Todas las apps → ReAppzuku → No optimizar**

---

### Fijar en Recientes (todos los firmwares)

Abre las apps recientes (botón cuadrado o deslizar desde abajo), busca ReAppzuku y toca el **ícono de candado** 🔒. Esto evita que se descargue de la memoria cuando limpias las apps recientes.

---

### MIUI / HyperOS (Xiaomi, Redmi, POCO)

<details>
<summary>Expandir instrucciones</summary>

**Inicio automático:**

Ajustes → Apps → Gestionar apps → ReAppzuku → Inicio automático → Habilitar

**Actividad en segundo plano:**

Ajustes → Apps → Gestionar apps → ReAppzuku → Ahorro de batería → Sin restricciones

**Bloquear en recientes:**

Recientes → mantener presionada la tarjeta de ReAppzuku → tocar el ícono de candado

**Adicional (MIUI 12+):**

Ajustes → Apps → Gestionar apps → ReAppzuku → Otros permisos → Ejecutar en segundo plano → Permitir

</details>

---

### One UI (Samsung)

<details>
<summary>Expandir instrucciones</summary>

**Permitir actividad en segundo plano:**

Ajustes → Mantenimiento del dispositivo → Batería → Límites de uso en segundo plano → Apps inactivas → asegúrate de que ReAppzuku no esté en la lista

**Deshabilitar batería adaptable:**

Ajustes → Mantenimiento del dispositivo → Batería → Más ajustes de batería → Batería adaptable → Desactivado (opcional, si los problemas persisten)

**Inicio automático:**

Ajustes → Apps → ReAppzuku → Batería → Sin restricciones

</details>

---

### ColorOS / OxygenOS (OPPO, OnePlus, Realme)

<details>
<summary>Expandir instrucciones</summary>

**Inicio automático:**

Ajustes → Gestión de apps → ReAppzuku → Inicio automático → Habilitar

**Actividad en segundo plano:**

Ajustes → Gestión de apps → ReAppzuku → Ahorro de batería → No restringir

**Adicional:**

Ajustes → Batería → Optimización de batería → ReAppzuku → No optimizar

</details>

---

### Flyme (Meizu)

<details>
<summary>Expandir instrucciones</summary>

**Inicio automático:**

Ajustes → Permisos → Inicio automático → ReAppzuku → Habilitar

**Actividad en segundo plano:**

Ajustes → Permisos → Ejecución en segundo plano → ReAppzuku → Habilitar

**Seguridad de la app:**

Centro de seguridad → Gestor de permisos → ReAppzuku → habilitar todos los permisos

</details>

---

### OriginOS / Funtouch OS (Vivo)

<details>
<summary>Expandir instrucciones</summary>

**Inicio automático:**

Ajustes → Apps → Gestionar apps → ReAppzuku → Permisos → Inicio automático → Habilitar

**Actividad en segundo plano:**

Ajustes → Apps → Gestionar apps → ReAppzuku → Consumo de energía → Alto rendimiento en segundo plano

</details>

---

### MagicOS (Honor)

<details>
<summary>Expandir instrucciones</summary>

**Inicio automático:**

Ajustes → Apps → Inicio de apps → ReAppzuku → Manual → Inicio automático, Actividad en segundo plano

**Batería:**

Ajustes → Batería → Inicio de apps → ReAppzuku → No restringir

</details>

---

### Cómo verificar que todo está configurado correctamente

Después de la configuración:
1. Habilita el **Servicio de segundo plano** en los ajustes de ReAppzuku
2. Bloquea la pantalla durante 10–15 minutos
3. Desbloquea y abre ReAppzuku — el servicio debería seguir activo
4. Si el servicio se detuvo — repite los pasos para tu firmware

> [!TIP]
> Instrucciones específicas por dispositivo: [dontkillmyapp.com](https://dontkillmyapp.com)

---

## ¿Por dónde empezar?
↩️[Tabla de contenidos](#table-of-contents)

> [!CAUTION]
> Esta sección tiene un carácter puramente informativo y no constituye una instrucción de uso de ReAppzuku. Configura todas las funciones por tu cuenta y de forma consciente, teniendo en cuenta que una configuración incorrecta de alguna función puede alterar el funcionamiento normal de las aplicaciones objetivo.

A primera vista, ReAppzuku puede parecer una herramienta compleja con una gran cantidad de ajustes. En realidad, no es tan difícil de entender.

**Configuración inicial**\
Concede los permisos de Root o Shizuku. Asegúrate de quitar a la aplicación todas las restricciones del sistema (optimización de batería, bloqueo de inicio automático, candado en recientes, etc.).\
Después de iniciar ReAppzuku, ve a los ajustes y activa el **Servicio en segundo plano**: esta opción pone en marcha los procesos en segundo plano de la aplicación, como el monitoreo de apps, la recopilación de estadísticas, etc.

**Recopilación de estadísticas**\
No actives Auto-Kill de inmediato ni configures otras restricciones apresuradamente. Dale a la aplicación entre 1 y 2 días para recopilar estadísticas: esto permitirá determinar con mayor precisión qué aplicaciones realmente sobrecargan el dispositivo en segundo plano.

**Análisis de datos**\
Después de recopilar los datos, ve a la sección **"Estadísticas y registros"** y revisa los gráficos de consumo (Batería, CPU, RAM). Adicionalmente, puedes comparar con las estadísticas de batería de los ajustes del teléfono. En la leyenda de los gráficos se puede ver qué aplicaciones consumen más recursos.

**Configuración de Auto-Kill**\
Se recomienda comenzar con el modo **Lista negra**, ya que es más seguro al afectar solo a las aplicaciones seleccionadas explícitamente, sin afectar al resto. El intervalo del Auto-Kill periódico puede establecerse inicialmente en 1 minuto, ajustándolo posteriormente según tus necesidades.\
Tras 1–2 horas de funcionamiento de Auto-Kill, ve a la sección "Estadísticas y registros" y abre el registro **Principales infractores**. Ahí verás qué aplicaciones fueron cerradas con más frecuencia y cuáles se reiniciaron de inmediato.\
Las aplicaciones que se reinician más de 3 veces son buenas candidatas para las Restricciones de segundo plano. Para empezar, se les puede asignar el tipo Suave / Soft de Restricción de segundo plano.

**Ajuste específico**\
Para un análisis más profundo, utiliza el botón **Disparadores de apps (App Triggers)** en la página Principal: muestra exactamente qué utiliza la aplicación para funcionar en segundo plano. Según el resultado, puedes aplicar otros tipos de Restricciones de segundo plano. Encontrarás más detalles al respecto en la sección correspondiente de la guía.

> [!TIP]
> Auto-Kill elimina el hecho de que la aplicación se ejecute en segundo plano, pero no la causa por la que vuelve a hacerlo. Para un control completo, se recomienda configurar adicionalmente las Restricciones de segundo plano y el Modo sueño (Sleep Mode).

---

## Control manual
↩️[Tabla de contenidos](#table-of-contents)

**Ajustes rápidos (Quick Tiles)**\
Añadidos al panel de notificaciones:

| Botón | Acción |
|---|---|
| **Kill App** | Cierra la app actual en primer plano |
| **Kill Background Apps** | Ejecuta el Auto-Kill con tus ajustes de lista blanca/lista negra |

**Widget**\
Widget para la pantalla de inicio — muestra las estadísticas de Auto-Kill de las últimas 12 horas y la carga actual de RAM.

**Acceso directo (Shortcut)**\
Acceso directo estático al mantener presionado el ícono de la app — cierra la app actual en primer plano de forma instantánea.

---

## Principal
↩️[Tabla de contenidos](#table-of-contents)

La pantalla principal muestra todas las apps activas en segundo plano con el uso de RAM y CPU en tiempo real. La sección superior muestra las estadísticas generales: número de apps activas y la carga actual de RAM.

### Barra de herramientas
↩️[Tabla de contenidos](#table-of-contents)

Tres botones en la barra de herramientas:

- 🔍 **Buscar** — filtra la lista por nombre de app o paquete
- 🔽 **Ordenar** — configura el orden de visualización
- ☑️ **Seleccionar todo** — selecciona todas las apps para el Kill con un solo toque

**Ordenar**

La lista se puede ordenar por:
- **Predeterminado** — apps de usuario primero, luego apps del sistema
- **Uso de RAM: Alto → Bajo / Bajo → Alto**
- **Carga de CPU: Alta → Baja / Baja → Alta**
- **Nombre A → Z** / **Nombre Z → A** — alfabético

También puedes alternar la visualización de las apps del sistema y las apps persistentes.

**Escanear**
Realiza un escaneo de la carga actual en el sistema de todas las apps activas en la lista. Categorías de carga:
- Retención de CPU
- Retención de red
- Retención de servicio de primer plano (Foreground Service)
- Despertando al dispositivo (impide el modo sueño)
- Retención de sensores
- Retención de GPS

> [!NOTE]
> El escaneo no funciona en apps persistentes ni protegidas, incluso si aparecen en la lista de apps activas.

> [!TIP]
> Ten en cuenta que cuantas más apps activas se muestren (por ejemplo, si está activada la visualización de apps del sistema), más tiempo tardará el escaneo.

### Acciones de la app

Al tocar una app en la lista se abre el menú de acciones rápidas:

- **Información de la app** — abre la información estándar de la app del sistema
- **Disparadores de la app** — análisis detallado de las causas de la actividad en segundo plano (ver abajo)
- **Desinstalar** — elimina la app del dispositivo (no disponible para apps del sistema)
- **Añadir a...** — añade rápidamente a una de estas listas:
  - Lista blanca
  - Lista negra
  - Ocultas
  - Restricción de segundo plano (Suave/Soft)

### Disparadores de apps (App Triggers)
↩️[Tabla de contenidos](#table-of-contents)

Los disparadores son una herramienta de diagnóstico profundo que analiza las **razones reales** de la actividad en segundo plano de una app a nivel del sistema. En lugar de suposiciones, ofrece hechos técnicos precisos: qué mantiene a la app en memoria, con qué frecuencia se despierta y si tiene conexiones de red activas en este preciso momento.

Analiza **63 factores independientes (43 principales y 20 adicionales dependiendo de la versión de Android)** mediante comandos del sistema en tiempo real.

---

**Estado de la app (activa / segundo plano / en caché)**\
Determinado por la prioridad del proceso en el kernel de Linux en combinación con la detección de servicios activos. Este es el mismo valor que utiliza Android para decidir qué procesos finalizar cuando la memoria es baja.

- **Activa** — la app está en primer plano o retiene recursos del sistema (servicio, alarma, etc.).
- **Segundo plano • servicio activo** — se ejecuta en segundo plano con un servicio de primer plano (Foreground Service) activo.
- **Segundo plano** — se ejecuta de forma silenciosa, pero el sistema la considera necesaria.
- **En caché • retiene servicio** — el proceso está en la caché pero mantiene un servicio activo en ejecución.
- **En caché • usada recientemente** — el proceso está en la caché y fue utilizado recientemente.
- **En caché • inactiva** — el proceso está vivo, pero Android está listo para finalizarlo en cualquier momento.

---

**Puntuación de agresión (Aggression Score)**\
Se evalúa en una escala de 100 puntos basada en los disparadores.
- Disparadores activos: + **6 puntos** cada uno.
- Puede despertar a la app en cualquier momento: + **5 puntos** cada uno.
- Otros disparadores: **0–4 puntos** dependiendo de la importancia. Algunos son solo informativos y no afectan la puntuación.

> [!TIP]
> Qué puedes hacer según la puntuación de agresión:
> - 0–40 — el sistema puede manejar esto por sí solo. No hay necesidad urgente de restricciones.
> - 41–65 — nivel medio. El Auto-Kill o el tipo Suave (Soft) de las restricciones de segundo plano pueden ser suficientes.
> - 66+ — candidato ideal para Auto-Kill, tipo Estricto (Hard) o Manual de restricciones de segundo plano, o el Modo sueño.

> [!CAUTION]
> Esta nota se proporciona únicamente con fines informativos y no debe tratarse como una recomendación. Decide si deseas aplicar restricciones a una app basándote en factores como:
> - el comportamiento de la app.
> - sus disparadores y puntuación de agresión.
> - el estado actual asignado por el sistema.
> - el uso de recursos del dispositivo (batería, RAM, CPU).

---

#### Tipos de disparadores:

**Actuales (Actual)**

La app está consumiendo recursos **justo ahora**.

- **Servicio de primer plano (Foreground Service)**. 
La app inició un servicio en segundo plano con una notificación persistente. Es la forma más confiable de evitar ser cerrada: Android no tocará estos procesos mientras la notificación sea visible. Muestra el tipo de servicio: reproducción de medios, ubicación, llamada telefónica, dispositivo conectado, etc.

- **Canal de notificaciones en primer plano (FG Notification Channel)**.
Complementa la información del servicio de primer plano: muestra la importancia del canal de notificaciones. La importancia URGENTE o ALTA se muestra como un banner emergente; es extremadamente difícil de suprimir para el sistema, lo que hace que la detención forzada sea casi imposible.

- **Servicio persistente (Sticky Service)**.
El servicio se declaró como `START_STICKY`: Android lo reinicia automáticamente después de ser cerrado. La app no se puede detener de forma permanente sin deshabilitarla.

- **Retenido por vinculaciones (Held by Bindings)**.
Uno o más procesos mantienen una vinculación (binding) activa con el servicio de esta app. Mientras se mantenga la vinculación, Android no puede cerrar el proceso. Google Play Services (GMS) suele ser el culpable común: retiene conexiones push y vinculaciones de sincronización de cuentas.

- **WakeLock**.
La app solicitó explícitamente al sistema "permanecer despierta". `PARTIAL_WAKE_LOCK` — la CPU se ejecuta con la pantalla apagada; `FULL_WAKE_LOCK` — la pantalla también permanece encendida. Muestra la etiqueta del bloqueo, el tipo y la duración de la retención. Agota directamente la batería mientras se mantiene activo.

- **Actividad de red (Network Activity)**.
La app tiene actividad de red activa en segundo plano. Las conexiones TCP abiertas indican un intercambio de datos continuo, algo típico en mensajería, clientes push y apps de sincronización en tiempo real. Solo se cuenta el tráfico que supera los 10 KB, junto con las conexiones `ESTABLISHED`.

- **Sensores**.
La app está realizando encuestas activas a los sensores de hardware: acelerómetro, giroscopio, barómetro, GPS, monitor de frecuencia cardíaca y otros. El uso continuo de sensores agota la batería incluso con la pantalla apagada. Muestra los nombres de los sensores y la frecuencia de muestreo donde esté disponible.

- **Ubicación**.
La app está solicitando datos de ubicación. Muestra el nivel de precisión (HIGH_ACCURACY, BALANCED, LOW_POWER), si es en segundo o primer plano, y el intervalo mínimo de actualización. La ubicación en segundo plano con alta precisión es la que más recursos consume.

- **Enfoque de audio (Audio Focus)**.
La app retiene el enfoque de audio, ya sea de forma exclusiva (GAIN) o temporal (duck/transient). El proceso permanece vivo hasta que se libera el enfoque. Muestra el tipo de transmisión: MUSIC, VOICE_CALL, ALARM, etc.

- **Sesión de medios (Media Session)**.
La app tiene una `MediaSession` activa. Muestra el estado de la reproducción (PLAYING, PAUSED, BUFFERING, etc.) y la etiqueta de la sesión. Una sesión pausada y no cerrada es una razón común por la que las apps de medios permanecen en memoria.

- **Escaneo BLE**.
La app está realizando un escaneo de Bluetooth de baja energía (Bluetooth Low Energy). El escaneo BLE adquiere un wake lock de forma interna y mantiene el proceso ejecutándose en segundo plano. El modo `LOW_LATENCY` es el que más energía consume.

- **Conexión GATT**.
La app tiene una conexión Bluetooth GATT activa con un periférico. El sistema mantiene la conexión y conserva el proceso vivo durante su duración.

- **AppOps**.
Operaciones de AppOps que indican una actividad reciente de la app:
  - **WAKE_LOCK** — la app adquirió un WakeLock mediante AppOps; la CPU se mantuvo despierta en su nombre.
  - **ACTIVITY_RECOGNITION** — la app utiliza la API de reconocimiento de actividad y recibe periódicamente actualizaciones de movimiento en segundo plano (caminando, corriendo, en un vehículo, etc.).

<details>
<summary>Disparadores de Android 15+</summary>

- **Tiempo límite de FGS excedido (FGS Timeout Exceeded)**.
Android 15: un servicio del tipo `dataSync` o `mediaProcessing` excedió el límite de 6 horas. El sistema debería haber activado `onTimeout()` y haberlo detenido.

- **FGS cerca del tiempo límite (FGS Near Timeout)**.
Android 15: al servicio le quedan menos de 30 minutos de su límite de 6 horas (`dataSync` / `mediaProcessing`).

</details>

<details>
<summary>Disparadores de Android 13 y anteriores</summary>

- **WakeLock (atribución de WorkSource)**.
Android 10–13: un proceso del sistema retiene el wakelock, pero se atribuye a esta app mediante WorkSource. La app es la iniciadora real de la activación, aunque formalmente el bloqueo lo retenga el sistema.

- **WakeLock de Kernel (Kernel Wakelock)**.
La app retiene un wakelock a nivel de kernel (`/sys/power/wake_lock`). Extremadamente raro: indica un controlador no estándar o un componente del sistema.

- **ACCESS_BACKGROUND_LOCATION**.
Android 11–13: la app tiene permiso para recibir datos de ubicación desde el segundo plano en cualquier momento, incluso cuando no está en uso activo. Requiere una aprobación independiente del usuario.

</details>

---

**Puede despertar en cualquier momento (Can Wake Up at Any Time)**\
El sistema **puede iniciar o reanudar** la app sin ninguna acción del usuario.

- **Alarmas (Alarms)**.
Analiza las alarmas activas de `AlarmManager`. Las alarmas de activación (`RTC_WAKEUP`) sacan al dispositivo de la suspensión incluso con la pantalla apagada. Un intervalo de menos de 2 minutos se considera de alta gravedad. Las alarmas `AllowWhileIdle` se activan incluso en modo Doze. Muestra las etiquetas de las alarmas, los intervalos y el tiempo restante hasta el próximo disparo.

- **Tareas / WorkManager (Jobs / WorkManager)**.
La app ha registrado tareas en `JobScheduler`. Las tareas de WorkManager, las tareas de sincronización y las operaciones periódicas se registran aquí y despiertan a la app según la programación. Muestra las restricciones de la tarea (tipo de red, requerimiento de carga, modo inactivo) y las razones de detención del historial reciente.

- **PendingIntent**.
La app retiene `PendingIntent`s registrados. El sistema u otras apps pueden activarlos en cualquier momento — a través de una notificación, AlarmManager o un evento del sistema — iniciando el proceso. Muestra el desglose por tipo: Activity, Service, Broadcast.

- **Activaciones excesivas (Excessive Wakeups)**.
Total de activaciones del dispositivo provocadas por esta app desde la última carga. Los números altos indican una actividad agresiva en segundo plano que impide el sueño profundo de la CPU. Se desglosa por alarmas, tareas, GCM/FCM y difusiones (broadcasts).

- **Contenedores de contenido (Content Observers)**.
La app registró `ContentObserver`s para URIs de contenido (contactos, medios, ajustes, calendario, etc.). Cualquier cambio en esas URIs despierta a la app para entregar la devolución de llamada (callback).

- **Notificaciones Push (FCM)**.
La app está registrada para Firebase Cloud Messaging (FCM). Google Play Services puede despertarla en cualquier momento cuando llega un mensaje push, sin importar los ajustes de optimización de batería.

- **Receptores dinámicos (Dynamic Receivers)**.
La app registró `BroadcastReceiver`s de forma dinámica durante la ejecución. A diferencia de los receptores estáticos del manifiesto, estos están activos mientras el proceso está vivo y reaccionan a los eventos del sistema en tiempo real.

- **AppOps**.
Operaciones de AppOps que conceden derechos de ejecución en segundo plano:
  - **RUN_IN_BACKGROUND** — la política de batería del sistema permite explícitamente que esta app se ejecute en segundo plano. No se suspenderá cuando la pantalla esté apagada.
  - **RUN_ANY_IN_BACKGROUND** — la app está completamente excluida de la optimización de batería: ejecución ilimitada en segundo plano sin limitaciones del sistema.
  - **USE_FULL_SCREEN_INTENT** — permiso para mostrar notificaciones sobre la pantalla de bloqueo. Android 14+: permitido solo para apps de alarmas o llamadas. Su presencia en una app de terceros es una anomalía.
  - **RUN_USER_INITIATED_JOBS** — permiso para ejecutar tareas largas iniciadas por el usuario. Puede ejecutarse mientras la pantalla está bloqueada.
  - **USER_INTERACTION** — la app recibió recientemente una señal explícita de interacción del usuario, lo que pudo haber provocado un inicio en segundo plano.

<details>
<summary>Disparadores de Android 14+</summary>

- **Tareas (alternativa sysfs)**.
Android 14+: estado de la tarea obtenido mediante `cmd jobscheduler get-job-state` cuando el método principal (`dumpsys jobscheduler`) no está disponible. Muestra el estado: en ejecución, pendiente o detenido.

</details>

<details>
<summary>Disparadores de Android 13 y anteriores</summary>

- **SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM**.
Android 12–13: la app tiene permiso para alarmas exactas que se activan a una hora específica sin importar el modo Doze y el ahorro de batería. `USE_EXACT_ALARM` es un derecho más amplio concedido únicamente a apps de despertador y calendario.

</details>

---

**Otros disparadores (Other Triggers)**\
Factores pasivos que afectan el comportamiento en segundo plano pero no indican actividad actual de forma directa.

- **Lanzamiento en cadena (Chain Launch)**.
Identifica quién inició este proceso y cómo. Llamada directa: otra app lo inició explícitamente a través de un servicio o actividad. Difusión (Broadcast): iniciado por una difusión de una app de terceros. Muestra el nombre del remitente y la acción desencadenante.

- **Receptores de difusión (Broadcast Receivers)**.
Muestra todos los eventos del sistema a los que la app se suscribió en el manifiesto: cambios de red, conexión del cargador, cambios de zona horaria, pantalla encendida/apagada y otros. Las suscripciones a `BOOT` y `CONNECTIVITY` están marcadas como potencialmente agresivas.

- **Inicio automático en el encendido (Boot Autostart)**.
La app está registrada para eventos de encendido del sistema. `BOOT_COMPLETED` — se lanza después de que se desbloquea el almacenamiento. `LOCKED_BOOT_COMPLETED` — se lanza antes de que aparezca la pantalla de bloqueo (antes de ingresar el PIN o contraseña); es un inicio automático especialmente agresivo.

- **Estado de espera de la app (App Standby Bucket)**.
Rango de prioridad de la app en el sistema: `ACTIVE` → `WORKING_SET` → `FREQUENT` → `RARE` → `RESTRICTED` → `NEVER`. Mayor estado = menos restricciones en segundo plano. `RESTRICTED` y `NEVER` significan que el sistema ya limitó la app. Muestra el historial de estados donde esté disponible.

- **Exento de Doze (Doze Exempt)**.
La app está en la lista blanca de Doze. Estas apps no entran en modo de suspensión con el dispositivo y conservan acceso irrestricto a la red y a las alarmas en cualquier momento. Las entradas del fabricante no pueden ser revocadas por el usuario.

- **Historial de uso de batería**.
Estadísticas desde el último restablecimiento de batería: retenciones de wakelocks, activaciones de alarmas, lanzamientos de tareas y sincronizaciones. Complementa la instantánea actual con datos a más largo plazo.

- **Eficiencia de difusión (Broadcast Efficiency)**.
Muestra cuántas difusiones se entregaron a la app y cuántas requirieron un inicio en frío. Un porcentaje alto = el sistema la cierra y la reinicia regularmente.

- **Múltiples procesos (Multiple Processes)**.
La app se ejecuta en más de un proceso del SO. Los subprocesos (`:sync`, `:remote`, `:push`, etc.) pueden mantenerse vivos de forma independiente y podrían no cerrarse cuando el proceso principal se detiene.

- **Servicio de accesibilidad (Accessibility Service)**.
La app está registrada como un Servicio de accesibilidad activo. El sistema la mantiene en ejecución en todo momento mientras esté habilitada, sin importar la optimización de batería.

- **Método de entrada (IME)**.
La app es el método de entrada (teclado) seleccionado actualmente. El sistema mantiene vivo el IME activo mientras esté seleccionado.

- **Administrador de dispositivo (Device Administrator)**.
La app es un Administrador de dispositivo activo, Propietario del dispositivo (Device Owner) o Propietario del perfil (Profile Owner). Cuenta con privilegios elevados: el sistema la protege contra el cierre forzado mediante los mecanismos estándar de restricción de batería.

- **Adaptador de sincronización (Sync Adapter)**.
La app tiene un Sync Adapter registrado en el sistema. Android lo lanza periódicamente para sincronizar los datos de la cuenta, incluso cuando la app no se está ejecutando.

- **Inicio en segundo plano (Background Start)**.
La app estuvo activa recientemente pero no en primer plano; es señal de una activación oculta en segundo plano provocada por una alarma, tarea, mensaje push o lanzamiento en cadena. Se detecta comparando `lastTimeUsed` y `lastTimeForeground` desde `dumpsys usagestats`.

- **AppOps**
  - **START_FOREGROUND (bloqueado)** — el sistema bloqueó el derecho a iniciar el servicio de primer plano. La app está intentando operar en segundo plano pero está restringida.
  - **MANAGE_MEDIA** — gestiona las sesiones multimedia de otras aplicaciones. Asociado con el tipo de FGS `mediaProcessing` en Android 15.
  
- **Proveedor de contenido (ContentProvider)**.
La app ha declarado uno o más ContentProviders. Otras apps o el sistema pueden consultarlos directamente a través de una URI: Android iniciará automáticamente el proceso ante una solicitud entrante incluso si no se estaba ejecutando. Muestra las direcciones de autoridad de los proveedores registrados.
  
- **Historial de Wakelocks**.
Muestra el historial de los últimos 5 **WAKELOCK** retenidos por la app. Si la app retiene un wakelock demasiado tiempo, es una mala señal.

<details>
<summary>Disparadores de Android 14+</summary>

- **Lanzamiento en cadena (privilegio BAL)**.
Android 14+: la app recibió un token `BackgroundStartPrivilege` para iniciarse desde segundo plano. Generalmente lo concede el sistema para FCM de alta prioridad, alarmas exactas o un PendingIntent de una app visible.

- **Inicio automático en el encendido (restricción FGS)**.
Android 14+: un receptor `BOOT_COMPLETED` no puede iniciar un FGS de tipo MICROPHONE o PHONE_CALL. La app está intentando evadir esta restricción en el encendido.

- **Exento de Doze (alternativa)**.
Android 14+: exención de optimización de batería detectada mediante `cmd appops get RUN_ANY_IN_BACKGROUND=allow`. La app puede ejecutarse en segundo plano sin restricciones de Doze/App Standby.

- **Efectos restringidos de StandbyBucket**.
Android 14+: el sistema confirmó el estado RESTRICTED mediante appops. Las tareas y las alarmas están bloqueadas; la app no puede iniciarse por sí misma de forma independiente.

</details>

<details>
<summary>Disparadores de Android 13 y anteriores</summary>

- **Lanzamiento en cadena (BAL bloqueado)**.
Android 13 y anteriores: el sistema bloqueó un intento de iniciar una Activity o FGS desde segundo plano sin una exención válida. La app intentó iniciar pero fue denegada.

- **Proceso congelado (Process Frozen)**.
Android 11–13: el proceso está congelado por el sistema mediante cgroup freezer; su ejecución está pausada pero no se ha cerrado. Se descongela automáticamente al acceder a él.

- **Inicio de FGS bloqueado (FGS Start Blocked)**.
Android 12–13: intento de iniciar un servicio de primer plano desde segundo plano sin una exención permitida. El servicio no se inició.

- **Red bloqueada (Ahorro de datos)**.
Android 10–13: el usuario habilitó el Ahorro de datos o restringió el acceso a la red en segundo plano. La app no puede usar la red en segundo plano con datos móviles.

- **Red en segundo plano permitida (Ahorro de datos)**.
Android 10–13: la app está en la lista blanca en los ajustes de Ahorro de datos; tiene acceso irrestricto a la red en segundo plano.

- **Permisos de BT (BLUETOOTH_SCAN / BLUETOOTH_CONNECT)**.
Android 12–13: la app tiene permisos para el escaneo y/o conexión Bluetooth. Puede iniciar un escaneo al recibir una difusión.

- **Receptores dinámicos (exported=true)**.
Android 13: un receptor registrado dinámicamente con `exported=true` es accesible para otras apps y puede recibir difusiones de cualquier remitente.

- **Alternativa de estado Doze (Doze State Fallback)**.
Android 11–13: el dispositivo está en modo Deep Doze o Light Doze. Los Wakelocks, la red, las tareas y las alarmas (excepto ALLOW_WHILE_IDLE) están bloqueados para las apps sin exención de Doze.

</details>

> [!TIP]
> Para usuarios Root: [Blocker](https://github.com/lihenggui/blocker) se complementa muy bien con ReAppzuku. Juntos te brindan un nuevo nivel de control sobre las apps.

---

## Ajustes

### 🔵 Información
↩️[Tabla de contenidos](#table-of-contents)

**Modo de acceso de ReAppzuku**\
Muestra el modo de acceso actual: **Root**, **Shizuku** o **Sin acceso**. Solo lectura.

**Ayuda**\
Enlace a estas preguntas frecuentes (FAQ).

---

### 🎨 Apariencia
↩️[Tabla de contenidos](#table-of-contents)

**Tema de la app**\
Elige un tema: predeterminado del sistema, claro, oscuro o AMOLED.

**Color de acento**\
Elige el acento de color: índigo, carmesí, verde bosque, ámbar y otros tonos.

**Notificaciones**\
Configura el comportamiento de las notificaciones. Las notificaciones críticas cubren el estado del servicio en segundo plano y los errores de permisos.

---

### ⚙️ Estabilidad de la app
↩️[Tabla de contenidos](#table-of-contents)

**Servicio de segundo plano**\
Interruptor principal de automatización. Inicia el proceso persistente en segundo plano de ReAppzuku. Se requiere para que funcionen la mayoría de las características de la app, incluida la recopilación de estadísticas.

---

### 🎯 Ajustes de Auto-Kill
↩️[Tabla de contenidos](#table-of-contents)

**Auto-Kill periódico**\
Cierra automáticamente las apps al intervalo establecido mientras el servicio de segundo plano esté ejecutándose.

**Intervalo de Auto-Kill:**

| Intervalo | Descripción |
|---|---|
| 10 segundos | Limpieza agresiva máxima |
| **18 segundos** | Predeterminado |
| 30 segundos | Limpieza moderada |
| 1 minuto | Limpieza ligera |
| 5 minutos | Intervención mínima |

**Kill al apagar la pantalla**\
Ejecuta el Kill en el instante en que se bloquea la pantalla. Útil para limpiar la memoria cada vez que dejas el teléfono.

**Kill por carga de RAM**\
Condición adicional — el Kill solo se activa **si** la RAM supera el umbral seleccionado. Se aplica tanto al Kill periódico como al Kill al apagar la pantalla.

| Umbral | Descripción |
|---|---|
| 75% | Limpieza temprana |
| **80%** | Predeterminado |
| 85–95% | Limpieza solo cuando la memoria es realmente baja |
| 100% | Solo para situaciones críticas |

**Tipo de Auto-Kill**\
Solo es relevante si ReAppzuku tiene conflictos con tu firmware. Si notas un comportamiento inusual en otras apps, intenta cambiar a `am kill`.

**Modo de Auto-Kill**\
Determina **cuáles** apps serán el objetivo de Auto-Kill.

- **🛡️ Lista blanca** — cierra todas las apps en segundo plano **excepto** las que están en la lista blanca. Úsalo para una limpieza máxima.

- **🎯 Lista negra (predeterminado)** — cierra **solo** las apps que están en la lista negra. Úsalo para detener apps específicas sin tocar todo lo demás.

**Lista blanca / Lista negra**\
Lista de apps para el modo seleccionado. Se muestra una de las dos listas dependiendo del modo.

**Condiciones avanzadas**\
Expande el Auto-Kill con disparadores adicionales — para casos en los que la programación regular no es suficiente.

- **Eventos de hardware**. 
Auto-Kill se ejecuta automáticamente ante eventos seleccionados: conectar/desconectar auriculares o USB, cambio en el estado de carga, WiFi, red móvil, Bluetooth, GPS o zona WiFi. Después del evento, se mantiene una pausa de 10 segundos — para que las apps parásitas tengan tiempo de iniciarse y puedan ser limpiadas.

- **Inicio de app**. 
Auto-Kill se activa justo cuando se abren las apps objetivo seleccionadas — útil en dispositivos de gama baja para liberar RAM y CPU antes de lanzar juegos o programas pesados. Las apps objetivo en sí mismas no son cerradas.  
  - **Limpiar caché**. Limpia adicionalmente la caché de todas las apps, excepto las protegidas, persistentes y las otras apps objetivo.
> [!IMPORTANT]
> La función **Inicio de app** requiere un permiso especial en los ajustes de "Accesibilidad". Esta característica también puede aumentar ligeramente el uso de batería de ReAppzuku.

**Ajustes preestablecidos de Auto-Kill**\
Guarda tu propio conjunto de ajustes de Auto-Kill que se activa automáticamente a una hora específica del día y reemplaza la configuración actual durante la duración de su ventana activa. Cuando la ventana termina, los ajustes originales se restauran automáticamente.
Hay **2 preajustes** disponibles. Cada uno se puede configurar de forma independiente: su propio nombre, su propio rango de tiempo activo, sus propias reglas de Auto-Kill, sus propias listas de apps y sus propios escenarios adicionales.
> [!WARNING]
> Mientras están activos, los preajustes ignoran la inmunidad otorgada a las apps por el Programador de restricciones. Esto se hace para evitar confusiones en los ajustes.

- **Habilitar preajuste**.
El interruptor principal. Si está deshabilitado, el preajuste **no se activará** según la programación, incluso si inicia su ventana de tiempo. Si el preajuste está activo actualmente y este interruptor se apaga, se desactiva inmediatamente y se restauran los ajustes originales.

- **Nombre del preajuste**.
Un nombre personalizado de hasta 30 caracteres. Se muestra en el diálogo de selección de preajustes en los ajustes principales. Si el preajuste está activo actualmente, aparecerá la etiqueta **"Activo"** junto a su nombre.

- **Horario activo**.
Un rango "Desde — Hasta", mostrado usando el formato de hora del dispositivo (12/24 horas). Se admiten rangos que cruzan la medianoche (por ejemplo, 22:00 – 06:00).
> [!WARNING]
> Los dos preajustes no pueden superponerse en su horario activo. Si intentas guardar un preajuste con un rango que se superpone, una advertencia mostrará el rango de tiempo del preajuste en conflicto — ajusta la hora de uno de los preajustes para solucionarlo.

- **Origen de la lista de apps**
Elige entre:
  - **Usar lista blanca / lista negra actual** — el preajuste siempre utiliza la lista blanca/lista negra activa de los ajustes principales en el momento en que se activa.
  - **Usar la lista propia del preajuste** — el preajuste tiene su propia lista blanca/lista negra independiente, que se edita por separado y no se ve afectada por los cambios en los ajustes principales.

- **Gestión de Auto-Kill y Condiciones avanzadas**.
Un bloque estándar de ajustes de Auto-Kill, igual al de los ajustes regulares de la app. Todos estos ajustes se describen en [Ajustes de Auto-Kill](#-auto-kill-settings)

- **Guardar preajuste**.
Aplica todos los cambios: guarda la configuración, vuelve a programar las alarmas de activación/desactivación y activa o desactiva inmediatamente el preajuste si es necesario (if los cambios afectan la ventana de tiempo actual).

- **Importar/Exportar archivo JSON**.
Guarda el preajuste en un archivo JSON o restáuralo desde un archivo de copia de seguridad. Para aplicar los cambios, haz clic en el botón "Guardar".

- **Restablecer preajuste**.
Restablece todos los ajustes actuales en pantalla a sus valores predeterminados (tomados de los ajustes principales de la app). **Los cambios no se aplican** hasta que se presione "Guardar" — puedes simplemente salir de la pantalla sin guardar y el restablecimiento no afectará al preajuste ya guardado.

**Acceso directo para Kill de RAM**\
Añade un pequeño acceso directo de 1x1 en el escritorio que muestra el uso de RAM en tiempo real en porcentaje y GB.\
Al tocar el acceso directo se activa un Auto-Kill instantáneo basado en los ajustes actuales y se limpia la RAM.

> [!TIP]
> La RAM se limpia de todos modos, ya sea que las apps se hayan cerrado durante el Auto-Kill o no. Para limpiar la RAM se utiliza el comando am send-trim-memory. Solo la lista blanca y las aplicaciones persistentes no se ven afectadas.

---

### 🔧 Herramientas avanzadas

#### Restricciones de segundo plano
↩️[Tabla de contenidos](#table-of-contents)

> [!WARNING]
> Disponible únicamente en **Android 11+**

Utiliza el comando `appops` de Android para **bloquear la ejecución de una app en segundo plano a nivel del SO**. Es más profundo que un Kill regular.

| | Kill regular | Restricciones de segundo plano |
|---|---|---|
| Cómo funciona | Fuerza la detención del proceso | Evita que Android inicie el proceso en segundo plano |
| Puede reiniciarse | ✅ Sí | ❌ No |
| Persiste tras reiniciar el equipo | ❌ No | ✅ Sí |
| Requiere Android 11+ | ❌ No | ✅ Sí |

**Tipos de restricciones:**
- **Suave / Soft** (ignorar RUN_ANY_IN_BACKGROUND)\
Bloquea el inicio automático a un nivel más estricto que los ajustes de actividad estándar.\
**Cómo funciona**: Si abres la app y cambias a otra — sigue ejecutándose (mientras esté en recientes). Pero por sí sola (durante la noche o en segundo plano) no se despertará hasta que la abras.

- **Media / Medium**\
Restringe cierta actividad en segundo plano.
**Cómo funciona:**\
Bloquea el lanzamiento de servicios, el programador de tareas (job scheduler) y las alarmas. La app funciona normalmente mientras está abierta, pero entra en modo de espera tan pronto como la dejas (al minimizarla).\
**Comandos utilizados:**\
`RUN_ANY_IN_BACKGROUND ignore`\
`RUN_IN_BACKGROUND ignore`\
`SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS ignore`\
`GET_USAGE_STATS ignore`\
`ACCESS_NOTIFICATIONS ignore`\
`Standby Bucket: Rare`

- **Estricta / Hard**\
Bloquea cualquier actividad en segundo plano.\
**Cómo funciona:**\
Una vez que la app se minimiza o cambias a otra — el sistema la cierra inmediatamente. La app no puede mantenerse en memoria sin la interacción directa del usuario (incluso si es visible en recientes). Usa la restricción Estricta con precaución, ya que puede privar por completo a la app de operaciones en segundo plano (descargas de archivos, reproducción de medios, tareas internas de larga duración).\
**Comandos utilizados:**\
`RUN_ANY_IN_BACKGROUND ignore`\
`RUN_IN_BACKGROUND ignore`\
`START_FOREGROUND ignore`\
`SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS ignore`\
`GET_USAGE_STATS ignore`\
`WAKE_LOCK ignore`\
`SCHEDULE_EXACT_ALARM ignore`\
`INTERACT_ACROSS_PROFILES ignore`\
`ACCESS_NOTIFICATIONS ignore`\
`Eliminación de la lista blanca de optimización de batería`\
`Standby Bucket: Restricted`

- **Manual**\
Tú eliges qué restricciones aplicar.\
**Cómo funciona**: ReAppzuku aplica únicamente las restricciones que selecciones.

> [!IMPORTANT]
> El estado de espera (App Standby Bucket) se restablece cuando el usuario interactúa con la app objetivo. El sistema no siempre lo vuelve a restaurar por sí solo. ReAppzuku restablecerá automáticamente el estado Bucket de la app en el siguiente ciclo de verificación de integridad de restricciones.

**Restricciones disponibles:**
- **RUN_ANY_IN_BACKGROUND**\
Evita que la app inicie procesos o servicios en segundo plano sin la interacción explícitamente del usuario. Es la restricción principal y más amplia — utilizada en el modo **Suave (Soft)**.\
**Bloquea:** inicios de servicios en segundo plano, sincronización y tareas diferidas (JobScheduler, WorkManager).\
**No bloquea:** servicios de primer plano (con notificación) ni procesos que ya estén en ejecución.

- **RUN_IN_BACKGROUND**\
Restricción de ejecución en segundo plano más específica. Bloquea el inicio de servicios a través de `startService()` cuando la app está en segundo plano.\
**Bloquea:** servicios en segundo plano iniciados por la propia app sin la intervención del usuario.\
**No bloquea:** servicios de primer plano, tareas activadas por alarmas ni receptores de difusión (broadcast receivers).

- **START_FOREGROUND**\
Evita que la app eleve un servicio a primer plano (notificación persistente). Sin esto, la app no puede mostrar la notificación de "ejecutándose en segundo plano" ni mantener el proceso vivo.\
**Bloquea:** llamadas a `startForeground()` — la app no puede crear una notificación fija ni mantener el servicio vivo.\
**No bloquea:** notificaciones regulares de la app ni tareas en segundo plano a través de JobScheduler.

- **SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS**
Impide que la aplicación eluda las restricciones de ahorro de energía del sistema (como los modos Doze o App Standby). Por lo general, este permiso permite que las aplicaciones del sistema y las aplicaciones críticas se ejecuten en segundo plano sin limitaciones.\
**Bloquea:** la ejecución de la aplicación en segundo plano sin restricciones, el uso libre de la red y el despertar del dispositivo (wakelocks) cuando los algoritmos de ahorro de batería están activos.\
**No bloquea:** el funcionamiento normal de la aplicación cuando está abierta en la pantalla (en primer plano), así como las funciones básicas en segundo plano permitidas por la gestión de batería estándar.

- **GET_USAGE_STATS**
Prohíbe a la aplicación acceder a las estadísticas de uso del dispositivo por parte de otras aplicaciones (qué apps se abrieron, cuánto tiempo se usaron y el historial de actividad).\
**Bloquea:** Las llamadas a los métodos de UsageStatsManager (por ejemplo, queryUsageStats(), queryEvents()); la aplicación no podrá rastrear qué programas usa el usuario ni cuánto tiempo pasa en ellos.\
**No bloquea:** El acceso de la aplicación a sus propias estadísticas de funcionamiento o a información básica del sistema no relacionada con la actividad de terceros.

- **WAKE_LOCK**\
Evita que la app mantenga activa la CPU con la pantalla apagada. Sin un wake lock, el sistema puede suspender la CPU y detener las operaciones en segundo plano.\
**Bloquea:** la retención de la CPU a través de `PowerManager.WakeLock` — la app no puede evitar que el teléfono entre en modo de suspensión.\
**No bloquea:** la ejecución de la app mientras la pantalla está encendida.

- **SCHEDULE_EXACT_ALARM**
Impide que la aplicación programe alarmas exactas mediante `AlarmManager.setExact()` y métodos similares. Esta restricción bloquea el registro de la alarma en sí, no solo la capacidad de despertar el dispositivo.\
**Bloquea:** llamadas a `setExact()`, `setExactAndAllowWhileIdle()` y otros métodos exactos de AlarmManager — la aplicación no podrá registrar una tarea diferida con tiempo preciso.\
**No bloquea:** temporizadores inexactos (`setInexactRepeating()`), tareas de JobScheduler y WorkManager.

- **INTERACT_ACROSS_PROFILES**\
Evita que la app interactúe con otros perfiles de trabajo. Relevante principalmente en dispositivos empresariales.\
**Bloquea:** llamadas entre perfiles y la transferencia de datos entre el perfil principal y el de trabajo.\
**No bloquea:** el funcionamiento de la app dentro de un solo perfil.

- **ACCESS_NOTIFICATIONS**
Prohíbe a la aplicación acceder al servicio de escucha de notificaciones. Esta restricción evita que la aplicación lea, intercepte o interactúe con las notificaciones de otros programas.\
**Bloquea:** El funcionamiento de NotificationListenerService; la aplicación no podrá leer el texto ni los títulos de las notificaciones de otras apps, descartarlas o pulsar programáticamente sus botones de acción.
**No bloquea:** La capacidad de la propia aplicación para enviar sus propias notificaciones al usuario a través de NotificationManager.

- **Estado Standby: Raro (Rare)**\
Marcado por el sistema como usado raramente. Bloquea la app a nivel del sistema:
  - Red en segundo plano. La red solo está disponible durante las raras ventanas de mantenimiento del sistema.
  - JobScheduler. Las tareas regulares y las tareas aceleradas (Expedited Jobs) se limitan a 10 minutos por día.
  - AlarmManager. Las alarmas inexactas se posponen. Límite — 1 disparo por hora.
  - Push (FCM). Se reduce la cuota de mensajes push de alta prioridad. Los mensajes push que excedan el límite se retrasan.

- **Estado Standby: Restringido (Restricted)**\
Marcado por el sistema como una app que no se usa desde hace mucho tiempo o que es anómala y consumió un exceso de CPU y batería. Incluye todas las restricciones del estado Raro, pero las aplica de forma más estricta. Adicionalmente restringe a nivel del sistema:
  - Eliminación de la exención de carga. Cuando el dispositivo está enchufado, las restricciones para todos los estados (incluido Raro) se levantan por completo. Sin embargo, para el estado Restringido, los límites de lanzamiento de JobScheduler permanecen activos incluso durante la carga.
  - Límite estricto de frecuencia de tareas. Limita rigurosamente la granularidad de la programación — la app tiene permitido lanzar una tarea en segundo plano exactamente 1 vez al día.
  - Comportamiento en el encendido. A partir de Android 13, si la app está en el estado Restringido, el sistema bloquea por completo la entrega de las difusiones `BOOT_COMPLETED` y `LOCKED_BOOT_COMPLETED`. La app no puede iniciarse al encender el SO hasta que el usuario la abra manualmente.
  - Finalización forzada de servicios activos. Si el sistema mueve una app en ejecución al estado Restringido mientras está en segundo plano (por ejemplo, debido a un consumo de energía anormal detectado), el SO elimina y finaliza automáticamente todos sus servicios de primer plano activos.
  - Acceso a la red durante las ventanas de mantenimiento. Durante el modo Doze, el sistema abre periódicamente ventanas de mantenimiento. A las apps con el estado Restringido se les niega el acceso a la red incluso durante estas ventanas del sistema.
  - Reducción del límite de tareas aceleradas. El límite para las tareas aceleradas (Expedited Jobs) se reduce a la mitad — bajando a 5 minutos por día.

**Comparación de tipos de restricciones**

| Restricción | Suave (Soft) | Media (Medium) | Estricta (Hard) | Manual |
|---|:---:|:---:|:---:|:---:|
| RUN_ANY_IN_BACKGROUND | ✓ | ✓ | ✓ | opcional |
| RUN_IN_BACKGROUND | — | ✓ | ✓ | opcional |
| START_FOREGROUND | — | — | ✓ | opcional |
| SYSTEM_EXEMPT_FROM_POWER_RESTRICTIONS | — | ✓ | ✓ | opcional |
| GET_USAGE_STATS | — | — | ✓ | opcional |
| WAKE_LOCK | — | — | ✓ | opcional |
| SCHEDULE_EXACT_ALARM | — | — | ✓ | opcional |
| INTERACT_ACROSS_PROFILES | — | — | ✓ | opcional |
| ACCESS_NOTIFICATIONS | — | ✓ | ✓ | opcional |
| Estado Standby Bucket | — | Rare | Restricted | opcional |

**Estados de la lista**:
- **Guardado en ReAppzuku** — guardado, pero se desconoce el estado del sistema (permisos insuficientes).
- **Guardado en ReAppzuku, pero no aplicado** — guardado, pero Android no ha aplicado la restricción.
- **Restringido, no por ReAppzuku** — restringido por Android o por otra app.

**Guardián de restricciones de segundo plano (Watchdog)**\
Una característica automatizada de ReAppzuku que comprueba periódicamente la integridad de las restricciones de segundo plano. Si el sistema restablece alguna restricción, el Watchdog la restaura automáticamente.\
Para los modos **Suave (Soft) y Medio (Medium)** (y Manual, si las restricciones elegidas son equivalentes a Suave/Medio) — las restricciones se restauran solo si la app no está activa en pantalla y no retiene `IMPORTANCE_FOREGROUND_SERVICE`.\
En todos los demás casos, las restricciones se restauran solo si la app no está activa actualmente en pantalla (not siendo utilizada).

**Volver a aplicar restricciones de segundo plano**\
Vuelve a aplicar manualmente todas las restricciones guardadas. Después de reiniciar, esto sucede de forma **automática** cuando se inicia el servicio de segundo plano.

---

#### Programador de restricciones
↩️[Tabla de contenidos](#table-of-contents)

Programa cuándo se deben levantar y restaurar las restricciones para apps específicas.
> [!IMPORTANT]
> Aquí solo aparecen las apps que tienen una **Restricción de segundo plano** activa (Suave / Media / Estricta / Manual).
> Las apps con una entrada programada muestran el ícono 🕐 junto con la hora programada.

Toca una app para abrir la configuración del programador:

**Proteger de**\
Selecciona de qué restricciones estará exenta temporalmente la app.

**Ventana de tiempo**\
Establece la hora de inicio (restricciones levantadas) y la hora de finalización (restricciones restauradas).
La app se detiene de forma forzada antes de que se restauren las restricciones.

**Establecer estado Bucket: Activo**
Cuando eliminas las restricciones de una app, su estado App Standby Bucket se fuerza a Activo (Active). Esto permite que la app ponga en marcha sus servicios por sí sola.

**Al activar**\
Acción a realizar cuando se levantan las restricciones:
- **Ninguna** — ninguna acción adicional.
- **Lanzar componente** — abre el selector de componentes de la app (Activity, Service, Receiver, etc.).

> [!NOTE]
> Las entradas programadas están limitadas a 15 apps para proteger a la propia ReAppzuku.

> [!IMPORTANT]
> El programador protege a las apps únicamente contra el tipo de congelación **temporal**.

---

#### Modo sueño (Sleep Mode)
↩️[Tabla de contenidos](#table-of-contents)

**Congela** por completo las apps seleccionadas cuando el dispositivo está inactivo. A diferencia de las restricciones de segundo plano, la app simplemente no puede iniciarse; está completamente deshabilitada por el sistema.
También puedes congelar una app de forma **permanente** directamente en el diálogo de la lista de apps.

Para cada app (Temporal o Permanente) puedes elegir el comando de congelación:
- **pm disable** — la app queda completamente deshabilitada por el sistema, su ícono puede desaparecer o moverse en la pantalla de inicio. Es la congelación más confiable; la app no podrá iniciarse.
- **pm suspend** — la app se oculta y bloquea sin deshabilitarse, su ícono permanece en su lugar. Es una congelación un poco menos confiable; la app se suspende, pero aún podría tener cierta actividad en segundo plano.

> [!IMPORTANT]
> Para las apps del sistema solo está disponible el comando **pm suspend**.

> [!CAUTION]Ten cuidado al configurar el Modo sueño para las apps del sistema.\
> ReAppzuku protege la mayoría de las apps críticas del sistema (como com.android.systemui) contra la manipulación, pero no garantiza una seguridad del 100%.\
> Ten en cuenta que congelar apps del sistema sin pensar puede causar un bucle de reinicio (bootloop).

Cómo funciona la congelación **temporal**:\
1. La pantalla se apaga → el temporizador inicia.
2. El temporizador expira → las apps seleccionadas se congelan con el comando elegido.
3. La pantalla se enciende y se desbloquea → las apps se descongelan automáticamente.

> [!NOTE]
> Si la app objetivo estaba en la pantalla de inicio, después de usar el comando pm disable su ícono podría desaparecer o moverse. Este es el comportamiento propio de Android. Con pm suspend, el ícono permanece en su lugar.

**Lista de apps de Modo sueño**\
Elige qué apps congelar en el modo sueño y selecciona el comando de congelación (pm suspend/pm disable) para cada una de ellas.

**Temporizador de congelación**\
Periodo de inactividad tras el cual se activa la congelación: de **5 a 60 minutos** (predeterminado: 60 minutos).

**Guardián de Modo sueño (WatchDog)**\
Función automática de ReAppzuku que comprueba periódicamente la integridad de la congelación del modo sueño; si el sistema descongela alguna app, la vuelve a congelar con el comando elegido para ella.\
Solo funciona para el tipo de congelación "Permanente".

---

**Limpiar caché de todas las apps**\
Ejecuta `pm trim-caches` — limpia la caché de todas las apps a la vez.

**Apps ocultas**\
Las apps aquí no aparecen en la pantalla principal y el Auto-Kill nunca las toca. Útil para procesos de servicio que no necesitas ver.

**Copia de seguridad y restauración**\
Exporta e importa todos los ajustes en formato JSON. Incluye la lista blanca, la lista negra, las apps ocultas, las restricciones de segundo plano, el Modo sueño y todos los ajustes de automatización.

---

### ℹ️ Acerca de
↩️[Tabla de contenidos](#table-of-contents)

**Código fuente**\
Enlace al repositorio de GitHub.

**Buscar actualizaciones**\
Busca manualmente en GitHub si hay un nuevo lanzamiento y lo muestra si lo encuentra.\
La búsqueda automática de actualizaciones ocurre cada 12 horas.

**Telegram**\
Puedes escribirle al desarrollador de ReAppzuku en Telegram.

**Agradecimientos especiales**\
Una lista honorífica de usuarios que han contribuido al desarrollo de ReAppzuku.

**Depuración (Debug)**\
Habilita/deshabilita los registros de depuración.\
Para guardar los registros usa:
- aShell (para Shizuku)
- Qute Terminal Emulator (para Root)

O puedes usar cualquier otro emulador de terminal que te sea conveniente.\
Para mostrar los registros en la consola usa: `logcat -s ReAppzukuDebug`

**Menú de depuración**\
Menú para habilitar/deshabilitar las categorías de registro requeridas.

---

### 📊 Estadísticas y registros
↩️[Tabla de contenidos](#table-of-contents)

**Consumo de ReAppzuku**\
La parte superior de la pantalla muestra el **consumo de recursos propio de ReAppzuku** — RAM, CPU y batería — para que puedas evaluar su impacto en el dispositivo.

**Gráficos de uso de recursos**\
Gráficos interactivos del uso de RAM, CPU y batería de las apps rastreadas. Cambia entre los tipos de gráficos con las **flechas**.

| Periodo | Descripción |
|---|---|
| 2 horas | Últimas 2 horas |
| 6 horas | Últimas 6 horas |
| 12 horas | Últimas 12 horas |
| 24 horas | Últimas 24 horas |

> [!TIP]
> Toca una **app en la leyenda del gráfico** para abrir su **gráfico de actividad personal**

**Registro de Auto-Kill**\
Muestra la actividad de las últimas **12 horas**: conteo de Auto-Kill, reinicios, RAM liberada y la hora del último evento por app.

> [!TIP]
> Las apps que se reinician más de 3 veces son buenas candidatas para las Restricciones de segundo plano.

**Principales infractores**\
Clasifica las apps según una puntuación combinada (cierres + reinicios + uso de RAM). Filtra por: 12 horas / 24 horas / 7 días / todo el tiempo.

> [!NOTE]
> La puntuación muestra qué tan agresivamente interfiere la app con la gestión de segundo plano.\
>
> `Puntuación = cierres × 1 + reinicios × 2 + RAM liberada × 0.01`
>
> • Cierre (+1) — la app fue detenida de forma forzada.\
> • Reinicio (+2) — la app se volvió a lanzar tras ser detenida; vale el doble porque es una resistencia activa.\
> • RAM — cada 100 MB de memoria liberada añade +1 punto; por lo general es una contribución pequeña.

> [!IMPORTANT]
> La RAM liberada se cuenta solo si la app no se encuentra ejecutándose en el siguiente ciclo de Auto-Kill. Si se reinicia, reclama la misma RAM — ganancia neta 0%.

**Registro de restricciones de segundo plano**\
Registro detallado de las operaciones de restricción de segundo plano. Se almacena en la caché, máximo 200 entradas.

| Estado | Significado |
|---|---|
| `Sent` | Comando ejecutado con éxito (puede no haber sido aplicado por el sistema) |
| `Applied` | Restricción confirmada por el sistema (resultado al 100%) |
| `NOT APPLIED` | Comando ejecutado, pero el sistema no aplicó el cambio |
| `ERROR` | El comando falló con un error |
| `Skipped` | Operación no realizada (sin permisos, Android < 11, etc.) |
| `Verification unavailable` | No se pudo consultar el estado real del sistema |
| `Removed from whitelist` | App eliminada de las excepciones de optimización de batería |
| `Restored to whitelist` | App restaurada en las excepciones de optimización de batería |

> [!TIP]
> Al tocar una entrada en el Registro de restricciones de segundo plano se abren los detalles del registro. Allí puedes ver cuáles AppOps no se aplicaron o se restablecieron. También puedes verificar si cambió el estado Standby Bucket de la app.

**Registro de Modo sueño**\
Registra la fecha y hora de congelación/descongelación para las apps objetivo.

**Registro del programador**\
Contiene registros de la actividad del Programador de restricciones. Cada entrada muestra:
- Fecha y hora en que se levantaron/restauraron las restricciones.
- Qué tan exitosamente se restauraron las restricciones (OK / PARCIAL / FALLÓ).
- Tipo de detención forzada aplicada (según los ajustes de Auto-Kill).
- Qué componente de la app se estaba ejecutando cuando se levantó la restricción.

---

## Apps protegidas
↩️[Tabla de contenidos](#table-of-contents)

Estas apps **nunca se ven afectadas** por el Auto-Kill u otras restricciones, independientemente de los ajustes:

**Núcleo de Android y Google**
- Servicios de Google Play y Marco de servicios de Google
- Interfaz del sistema (System UI)
- Ajustes de Android
- Teléfono / Marcador, Contactos, Servicio de SMS, Servidor de telefonía
- Bluetooth
- Almacenamiento externo y módulo de medios
- Instalador de paquetes y controlador de permisos (variantes de AOSP y Google)
- Gboard (Teclado de Google)
- Servicio ADB/Shell
- Llavero de Android (TLS/VPN/Wi-Fi)
- Proveedores de ajustes, telefonía y SMS/MMS
- NFC
- Pila de red, pila de anclaje de red, resolvedor de DNS, diálogos de VPN

**Shizuku**
- Shizuku (ambas variantes: `rikka.shizuku.common` y `moe.shizuku.privileged.api`)

**Gestores de Root**
- Magisk
- KernelSU
- KernelSU Next
- APatch
- SukiSU / SukiSU Ultra

**Apps del sistema del fabricante**
| Fabricante | Apps protegidas |
|---|---|
| **Xiaomi / MIUI / HyperOS** | Centro de seguridad, lanzador de inicio, fondo de pantalla, cámara, protección del sistema, servicios principales, PowerKeeper |
| **Samsung (One UI)** | Mantenimiento del dispositivo, protección del dispositivo, Inicio de One UI, interfaz del teléfono, servidor de telefonía |
| **Oppo / Realme / OnePlus (ColorOS)** | Gestor del teléfono, lanzador del sistema, asistente inteligente |
| **Vivo / iQOO (Funtouch / OriginOS)** | iManager, lanzador de Vivo |
| **Huawei / Honor (EMUI / MagicOS)** | Optimización del sistema, Inicio de Huawei, Gestor del sistema de Honor |

**Determinadas dinámicamente**
- Teclado actual (detectado automáticamente en tiempo de ejecución)
- Lanzador actual (detectado automáticamente en tiempo de ejecución)

---

## Preguntas frecuentes (FAQ)
↩️[Tabla de contenidos](#table-of-contents)

**❓ Una app se reinicia inmediatamente después del Kill — ¿qué debo hacer?**

Añádela a las **Restricciones de segundo plano** — evita que Android la reinicie en segundo plano a nivel del SO.

---

**❓ Las restricciones de segundo plano se pierden después de un reinicio**

Habilita el **Servicio de segundo plano** — restaura automáticamente todas las restricciones guardadas después de un reinicio.

---

**❓ ¿Qué modo debería elegir — lista blanca o lista negra?**

Lista blanca — detiene todo excepto lo que importa. Lista negra — detiene solo apps específicas y deja todo lo demás tranquilo.

---

**❓ ¿Se requiere el servicio de segundo plano para el Kill manual?**

No. El Kill manual desde la pantalla principal, los accesos rápidos, el widget y el acceso directo funciona sin el servicio de segundo plano.

---

**❓ ¿Es seguro detener las apps del sistema?**

No. Detener o restringir las apps del sistema puede causar inestabilidad, congelamientos, pérdida de notificaciones y bucles de reinicio. ReAppzuku te advierte antes de afectar a las apps del sistema.

---

**❓ Modo sueño vs Restricciones de segundo plano — ¿cuál es la diferencia?**

Las restricciones de segundo plano evitan que la app se **lance** en segundo plano, pero permanece instalada y visible. El Modo sueño la **congela** por completo a nivel del sistema — como si estuviera deshabilitada — hasta que se desbloquea la pantalla.

---

**❓ Shizuku dejó de funcionar después de un reinicio**

Shizuku requiere reactivación después de cada reinicio (a menos que se use el modo ADB inalámbrico). Abre Shizuku y reinicia el servicio.

---

**❓ Una app simplemente no se puede cerrar — ¿qué debo hacer?**

Abre el menú de la app y selecciona **Disparadores (Triggers)**. Mostrará exactamente qué está manteniendo vivo el proceso: un servicio de primer plano, un wakelock, un servicio persistente o la vinculación desde otra app. Dependiendo del disparador — aplica **Restricciones de segundo plano** (suave, estricta o manual).

---

**❓ Modo sueño vs Restricción estricta (Hard) — ¿cuál es la diferencia?**

Ambos limitan agresivamente la actividad en segundo plano, pero de manera diferente. El Modo sueño **congela** la app cuando la pantalla está apagada y la descongela al desbloquear — sigue el horario de la pantalla. La restricción Estricta está **siempre activa**: la app no puede sobrevivir en segundo plano incluso cuando la pantalla está encendida y has cambiado a otra app. Para congelación nocturna — Modo sueño. Para apps crónicamente agresivas — Restricción estricta.

---

**❓ ¿Por qué cambiar el tipo de Kill de force-stop a am kill?**

`am force-stop` es una detención dura — cierra todos los procesos y borra el estado de la app. `am kill` es más suave — finaliza solo los procesos en segundo plano sin tocar el primer plano. Solo cambia si notas problemas en otras apps o conflictos de firmware — en algunos dispositivos `force-stop` es demasiado agresivo.
