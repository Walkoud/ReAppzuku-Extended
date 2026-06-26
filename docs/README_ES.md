[English](./README.md) | [Русский](./README_RU.md) | [简体中文](./README_ZH.md) | **Español** | [Українська](./README_UK.md)

---

![Logo](https://github.com/gree1d/ReAppzuku/blob/main/docs/images/logo.png)
<p align="center">
<img src="https://img.shields.io/github/v/release/gree1d/ReAppzuku?label=Release&" alt="Latest Release">
<img src="https://img.shields.io/github/downloads/gree1d/ReAppzuku/total?label=Downloads&color=a855f7" alt="Downloads">
<img src="https://img.shields.io/badge/License-GPLv3-64748b.svg" alt="License">
<img src="https://img.shields.io/badge/Android-6.0%2B-f97316.svg" alt="Android">
<img src="https://img.shields.io/badge/Root-Supported-brightgreen.svg"/>
<img src="https://img.shields.io/badge/Shizuku-Supported-brightgreen.svg"/>
</p>

ReAppzuku es un fork de Appzuku (Shappky) con un control mejorado sobre la actividad en segundo plano de las aplicaciones de Android.

Monitorea y detiene las aplicaciones que consumen RAM, agotan la batería y cargan la CPU en segundo plano.\
Cierre forzado manual con un solo toque, Kill periódico a través de un temporizador y restricciones de segundo plano flexibles para las aplicaciones seleccionadas.\
\
Se requieren privilegios de Root o Shizuku.

## ⚙️ Características

* **Automatización inteligente:**
  * Auto-Kill periódico: intervalos desde 10 segundos hasta 5 minutos.
  * Kill al bloquear la pantalla: fuerza la detención de los procesos en segundo plano inmediatamente después de que se apague la pantalla.
  * Umbral de RAM: el Kill se activa solo cuando el uso de la RAM alcanza un límite establecido (75%–100%).
  * Kill por eventos de hardware/inicio de app: el Kill se activa por eventos de hardware seleccionados o cuando se inicia la aplicación objetivo, con la opción de limpiar adicionalmente la RAM.
  * Ajustes preestablecidos de Auto-Kill: Personaliza y programa el comportamiento de Auto-Kill a horas específicas. 
* **Controles manuales:**
  * Pantalla principal: ve todos los procesos activos en segundo plano con su uso de RAM, selecciónalos y elimínalos en masa.
  * Ajustes rápidos (Quick Tiles): "Detener app" cierra la app actual en primer plano; "Detener apps de segundo plano" ejecuta el Auto-Kill con tus listas.
  * Widget de pantalla de inicio: muestra el uso actual de RAM y las estadísticas de Auto-Kill de las últimas 12 horas. 
  * Acceso directo de la app: mantén presionado el ícono de la app para cerrar la app actual en primer plano de forma instantánea.
* **Restricciones de segundo plano** (Android 11+):
  * Modo suave (Soft): bloquea el inicio automático a nivel del SO — la app sigue funcionando si la abriste, pero no se despertará por sí sola.
  * Modo medio (Medium): restricción parcial de la actividad de la app en segundo plano.
  * Modo estricto (Hard): finaliza inmediatamente el proceso cuando se minimiza, evitando que permanezca en memoria ni un solo segundo.
  * Modo manual: selecciona y aplica manualmente las restricciones requeridas para la app.
* **Programador de restricciones:** establece una ventana de tiempo para levantar temporalmente las restricciones, con el inicio opcional de componentes al activarse.
* **Modo sueño (Sleep Mode):** congelación total de las apps seleccionadas después de un temporizador de inactividad establecido (5–60 min), descongelación automática al desbloquear la pantalla.
* **Disparadores de apps (App Triggers):** herramienta de diagnóstico profundo que analiza las causas reales de la actividad en segundo plano — servicios de primer plano, servicios persistentes (sticky), wakelocks, alarmas, programador de tareas (job scheduler), conexiones de red, receptores de arranque (boot receivers) y 54 factores más (Depende de la versión de Android).
* **Análisis y Registros:**
  * Registro de Auto-Kill de las últimas 12 horas: cierres, reinicios y RAM liberada por app.
  * Clasificación de los principales infractores por consumo de RAM y frecuencia de reinicio (12h / 24h / 7d / todo el tiempo).
  * Registro de restricciones de segundo plano: aplicadas, error, no aplicadas — hasta 200 entradas.
  * Gráficos de uso de recursos (RAM, CPU, batería) para períodos de 2, 6, 12 y 24 horas.
* **Listas flexibles:** Lista blanca (exclusiones de Auto-Kill), Lista negra (objetivos de Auto-Kill), Apps ocultas (excluidas de la lista y del Auto-Kill por completo).
* **Copia de seguridad y restauración:** exporta e importa todos los ajustes a un archivo JSON — lista blanca, lista negra, apps ocultas, restricciones, Modo sueño y parámetros de automatización.

## 🛠 Requisitos

| Componente | Requisito |
|---|---|
| Android | 6.0+ (Las restricciones de segundo plano requieren 11+) |
| Acceso | Root o Shizuku |

## 🚀 Inicio rápido

* **Configurar el acceso:** instala y activa [Shizuku](https://github.com/thedjchi/Shizuku), o concede acceso root.
* **Operación en segundo plano:** deshabilita la optimización de batería para ReAppzuku y fíjala en las apps recientes — de lo contrario, el sistema podría cerrar el propio servicio de gestión.
* **Elige tu estrategia:** Lista blanca + Kill periódico para el máximo ahorro, o solo Lista negra para un control específico de aplicaciones particulares.

## 🛡 Seguridad

ReAppzuku protege automáticamente los procesos críticos del sistema — Google Play Services, System UI, el teclado actual, el lanzador (launcher) actual, telefonía, Bluetooth, NFC y el propio Shizuku. Las apps de sistema específicas de fabricantes (Xiaomi Security Center, Samsung Device Care, OPPO Phone Manager, etc.) también están protegidas.

## 🎨 Personalización

* Temas del sistema, claro, oscuro y AMOLED.
* Acentos de color configurables: Índigo, Carmesí, Verde bosque, Ámbar y más.

## 🌐 Traducción

¡Las traducciones son bienvenidas!\
Para ayudar a localizar la app:
* Envía un **Pull Request** con los cambios en `/values/strings.xml`, `README.md`, `HELP.md`.
* Abre un **Issue** y adjunta tus archivos `/values/strings.xml`, `README.md`, `HELP.md` (empaquétalos primero en un `.zip`).

## 🖼️ Capturas de pantalla

<p align="center">
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot1.jpg" width="100" alt="Screenshot 1">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot2.jpg" width="100" alt="Screenshot 2">
  </a>
  <a href="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg">
    <img src="https://raw.githubusercontent.com/gree1d/ReAppzuku/refs/heads/main/docs/images/screenshot3.jpg" width="100" alt="Screenshot 3">
  </a>
</p>

## Licencia

ReAppzuku está bajo la licencia [GNU General Public License v3.0](LICENSE).

## Créditos

Fork de [northmendo/Appzuku](https://github.com/northmendo/Appzuku).
<br><br>
>![Claude](https://img.shields.io/badge/Claude-D97757?logo=claude&logoColor=fff)
![Google Gemini](https://img.shields.io/badge/Google%20Gemini-886FBF?logo=googlegemini&logoColor=fff)
![Grok / xAI](https://img.shields.io/badge/Grok-000000?logo=xai&logoColor=white)
> ReAppzuku fue construido usando vibecoding — un enfoque donde una parte significativa del código fue generada con la ayuda de IA (LLM).
