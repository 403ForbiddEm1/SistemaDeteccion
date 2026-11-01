# Sistema de Detección Térmico-Gaseosa e Interfaz de Alerta Temprana (JavaFX + Arduino)

Aplicación de escritorio en Java que muestra, en una sola pantalla, el estado de un sistema de detección de
temperatura, gas y flama conectado a un Arduino. Muestra los valores en tiempo real, grafica las lecturas,
genera alertas cuando detecta condiciones de riesgo y guarda un historial de incidencias.

La interfaz está construida **solo con código Java** (sin FXML ni Scene Builder) y se diseña con **CSS**.
Tiene **modo claro** (por defecto) y **modo oscuro** (botón en el encabezado).

---

## 1. Qué hay que tener instalado

| Programa | Versión | Dónde está / cómo se obtiene | Para qué sirve |
|---|---|---|---|
| **JDK** (Java Development Kit) | **17 o superior** (probado con 25.0.4, Eclipse Adoptium) | Se instala en el PC. En este equipo: `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot` | Compilar y ejecutar el código Java |
| **Apache Maven** | **3.9.x** (probado con 3.9.9) | Se descomprime en el PC. En este equipo: `C:\apache-maven-3.9.9-bin\apache-maven-3.9.9` | Descargar las bibliotecas, compilar y ejecutar el proyecto |
| **JavaFX** | 21.0.5 | **No se instala a mano**: Maven lo descarga solo (carpeta `C:\Users\<usuario>\.m2`) | Crear la interfaz gráfica |
| **jSerialComm** | 2.11.4 | **No se instala a mano**: Maven lo descarga solo | Comunicar Java con el Arduino por el puerto serial |
| **Arduino IDE** | Cualquier versión reciente | Descarga desde arduino.cc | Subir el sketch `deteccion.ino` al Arduino |
| Biblioteca **DHT sensor library** (Adafruit) y **Adafruit Unified Sensor** | Las últimas | Se instalan desde el IDE: *Herramientas → Administrar bibliotecas* | Leer el sensor de temperatura DHT11 |

Otros requisitos:

- **Sistema operativo:** Windows 10/11 (los comandos de este README son de PowerShell).
- **Internet** la primera vez que se ejecuta, porque Maven descarga JavaFX y jSerialComm.
- **Hardware** (solo para la conexión real): Arduino, sensor DHT11, sensor de gas MQ-2, buzzer, LED y cable USB.
  El módulo de flama está contemplado en el código pero desactivado (`USAR_FLAMA false`).

Para comprobar que todo está instalado, ejecuta en la terminal:

```powershell
java -version
javac -version
mvn -version
```

---

## 2. Cómo arrancar el programa

1. Abre una terminal **PowerShell** (por ejemplo, la terminal de Antigravity).
2. Ubícate en la carpeta del proyecto, la que contiene el archivo `pom.xml`:

   ```powershell
   cd C:\Users\ppc\OneDrive\Desktop\SistemaDeteccion
   ```

3. **Solo si `mvn` no se reconoce** (por ejemplo, en una terminal nueva), ejecuta estas dos líneas:

   ```powershell
   $env:JAVA_HOME = Split-Path (Split-Path (Get-Command javac).Source)
   $env:Path += ";C:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin"
   ```

4. Ejecuta el programa:

   ```powershell
   mvn clean javafx:run
   ```

Se abre la ventana del dashboard en **modo simulación** (datos falsos, no hace falta el Arduino).
Para volver a ejecutarlo después de cambiar el código basta `mvn javafx:run`, que es más rápido.

### Qué hace cada comando

| Comando | Qué hace |
|---|---|
| `$env:JAVA_HOME = Split-Path (Split-Path (Get-Command javac).Source)` | Busca dónde está `javac`, sube dos carpetas (de `bin` a la carpeta del JDK) y guarda esa ruta en `JAVA_HOME`. Así Maven sabe qué JDK usar. Solo vale para esa terminal. |
| `$env:Path += ";...\apache-maven-3.9.9\bin"` | Agrega la carpeta de Maven a la lista de programas que la terminal conoce (`Path`). Así reconoce el comando `mvn`. Solo vale para esa terminal. |
| `mvn clean javafx:run` | `clean` borra la carpeta `target` (lo compilado antes). Después Maven descarga las dependencias si faltan, compila el código y `javafx:run` (plugin `javafx-maven-plugin`) ejecuta la clase principal `com.deteccion.Main`. |

### Dejar Maven configurado para siempre

Para no repetir el paso 3 en cada terminal, ejecuta esto una sola vez y vuelve a abrir la terminal:

```powershell
[Environment]::SetEnvironmentVariable("Path", [Environment]::GetEnvironmentVariable("Path","User") + ";C:\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin", "User")
[Environment]::SetEnvironmentVariable("JAVA_HOME", (Split-Path (Split-Path (Get-Command javac).Source)), "User")
```

---

## 3. Conectar el Arduino

1. Abre `arduino/deteccion/deteccion.ino` en el IDE de Arduino. Ajusta `USAR_TEMP`, `USAR_GAS`, `USAR_FLAMA` y los pines según tu montaje, y súbelo a la placa.
2. Abre el Monitor Serie a **9600 baudios** y comprueba que llega una línea por segundo, por ejemplo `T:27.0;G:312`.
3. **Cierra el Monitor Serie**: el puerto solo lo puede usar un programa a la vez.
4. En la aplicación: panel **Controles** → elige el puerto (COM3, COM4…) → **Conectar**. El Arduino se reinicia al conectarse, así que los datos tardan un par de segundos en aparecer.

### Conexiones del sketch

| Componente | Pin del Arduino |
|---|---|
| Sensor de temperatura DHT11 | 2 |
| Sensor de gas MQ-2 (salida analógica) | A0 |
| Módulo de flama (desactivado por ahora) | 3 |
| Buzzer (alarma sonora) | 8 |
| LED de alerta | 9 |

### Formato de los datos (Arduino → Java)

El Arduino envía una línea de texto por segundo, a 9600 baudios:

```
T:28.4;G:312;F:0
```

| Campo | Significado |
|---|---|
| `T` | Temperatura en °C (DHT11) |
| `G` | Gas, lectura analógica de 0 a 1023 (MQ-2) |
| `F` | Flama: 1 = detectada, 0 = no detectada |

Solo se envían los sensores activados. El que falta aparece como "Sin señal" en el dashboard.
Desde Java se envían dos comandos de un carácter: `M` (silenciar el buzzer) y `U` (reactivarlo).

---

## 4. Qué es cada cosa del proyecto

```
SistemaDeteccion/
├── pom.xml
├── README.md
├── arduino/
│   └── deteccion/deteccion.ino
└── src/main/
    ├── java/com/deteccion/
    │   ├── Main.java
    │   ├── DashboardController.java
    │   ├── models/
    │   │   ├── Sensor.java
    │   │   ├── SensorTemperatura.java
    │   │   ├── SensorGas.java
    │   │   ├── SensorFlama.java
    │   │   ├── Nivel.java
    │   │   └── Incidencia.java
    │   └── services/
    │       ├── ArduinoService.java
    │       └── GestorAlertas.java
    └── resources/com/deteccion/
        └── styles.css
```

| Archivo o carpeta | Qué es |
|---|---|
| `pom.xml` | Archivo de configuración de Maven: nombre del proyecto, versión de Java (17), dependencias (JavaFX y jSerialComm) y el plugin que ejecuta la aplicación con `javafx:run`. |
| `arduino/deteccion/deteccion.ino` | Programa del Arduino. Lee los sensores, enciende el LED y el buzzer cuando hay peligro y envía los datos por USB. |
| `Main.java` | Punto de entrada. Crea la ventana, carga el dashboard y la hoja de estilos `styles.css`. |
| `DashboardController.java` | Construye toda la pantalla con código (tarjetas, gráfica, tablas, botones), la actualiza con cada lectura y alterna el modo claro/oscuro. Conecta los sensores, el Arduino y las alertas. |
| `models/Sensor.java` | Clase base de todos los sensores: guarda el último valor, su nivel de riesgo y si hay señal. |
| `models/SensorTemperatura.java`, `SensorGas.java`, `SensorFlama.java` | Un sensor concreto cada una. Heredan de `Sensor` y definen sus propios umbrales y cómo se muestra su valor. |
| `models/Nivel.java` | Los tres niveles de riesgo: **Normal**, **Advertencia** y **Crítica**. |
| `models/Incidencia.java` | Un evento registrado: fecha y hora, sensor, valor, nivel y si ya está resuelta. Es lo que aparece en el historial. |
| `services/ArduinoService.java` | Comunicación con el Arduino por el puerto serial (jSerialComm) y el **modo simulación** que genera datos falsos. |
| `services/GestorAlertas.java` | Evalúa los niveles de riesgo, mantiene las alertas activas y el historial de incidencias, y gestiona el silencio de la alarma. |
| `resources/.../styles.css` | Hoja de estilos CSS: colores, tipografía, bordes y los dos temas (claro y oscuro). |
| `target/` | Carpeta que genera Maven al compilar. No se edita ni se entrega. |

### Cómo funciona todo junto

```
Sensores → Arduino → (USB, puerto serial) → ArduinoService → DashboardController
                                                              ├─ actualiza los Sensor (models)
                                                              ├─ GestorAlertas evalúa el riesgo y registra incidencias
                                                              └─ actualiza tarjetas, gráfica, alertas, historial y registro
```

- La lectura del puerto serial ocurre en un hilo aparte y cada dato se pasa a la interfaz con `Platform.runLater`, para que la pantalla **nunca se congele**.
- Si pasan más de 5 segundos sin datos, el dashboard muestra "Sin comunicación".
- Las tarjetas están en gris cuando todo es normal, en ámbar con una advertencia y en rojo con una condición crítica.

### Programación orientada a objetos en el proyecto

- **Herencia:** `SensorTemperatura`, `SensorGas` y `SensorFlama` heredan de `Sensor`.
- **Polimorfismo:** el dashboard trata a los tres como `Sensor` (por ejemplo, en la lista `sensores`) sin importar de qué tipo sean.
- **Encapsulación:** cada clase guarda su estado y lo expone mediante métodos.
- **Separación de responsabilidades:** la interfaz (`DashboardController`), la lógica (`models` y `GestorAlertas`) y el hardware (`ArduinoService`) están en clases distintas.

---

## 5. Ajustar umbrales

- **Temperatura:** `SensorTemperatura.java` (`LIMITE_ADVERTENCIA` y `LIMITE_CRITICO`).
- **Gas** (valor del conversor analógico, calibrar con el sensor real): `SensorGas.java`.
- **Alarma física del Arduino:** constantes `TEMP_CRITICA` y `GAS_CRITICO` en el `.ino`.

Los umbrales críticos de Java y del Arduino **deben coincidir**, para que la alarma física y la de la pantalla se activen al mismo tiempo.
Ten en cuenta que el DHT11 solo mide de 0 a 50 °C, así que el umbral crítico de temperatura debe ser menor que 50.
El MQ-2 necesita calentarse unos minutos para dar lecturas estables.

---

## 6. Problemas frecuentes

| Problema | Qué hacer |
|---|---|
| `mvn` no se reconoce | Ejecuta las dos líneas del paso 3 de la sección 2, o deja Maven configurado para siempre. |
| Error `Acceso denegado` con `jSerialComm.dll` | Windows o el antivirus bloquean la escritura del archivo. Revisa el *Historial de protección* de Seguridad de Windows y agrega una exclusión para `%TEMP%\jSerialComm`. Comprueba que el `pom.xml` usa jSerialComm 2.11.4 o superior. |
| El puerto COM no aparece | Revisa el cable USB y que el Arduino esté conectado. Después abre de nuevo el desplegable de puertos. |
| Aparece "Sin comunicación" con el Arduino conectado | Cierra el Monitor Serie del IDE, comprueba que el sketch envía a 9600 baudios y que elegiste el puerto correcto. |
| La temperatura nunca da alerta | El DHT11 no llega a 55 °C. Baja el umbral crítico en `SensorTemperatura.java` y en el `.ino`. |

---

## 7. Glosario

| Término | Significado |
|---|---|
| **JDK** | Kit de desarrollo de Java: incluye el compilador (`javac`) y la máquina virtual (`java`). |
| **Maven** | Herramienta que descarga bibliotecas, compila y ejecuta el proyecto según el `pom.xml`. |
| **JavaFX** | Biblioteca de Java para crear interfaces gráficas de escritorio. |
| **jSerialComm** | Biblioteca de Java para leer y escribir en puertos seriales (COM). |
| **Puerto serial / COM** | Canal por el que el Arduino envía texto a la computadora a través del cable USB. |
| **Baudios (bps)** | Velocidad de la comunicación serial. Aquí, 9600. |
| **DHT11** | Sensor digital de temperatura y humedad. Rango de 0 a 50 °C. |
| **MQ-2** | Sensor de gas y humo. Entrega un valor analógico de 0 a 1023. |
| **CSS** | Lenguaje de estilos; aquí define los colores y el aspecto de la ventana. |
| **Modo simulación** | Modo en que la aplicación genera datos falsos para probarla sin Arduino. |
| **Incidencia** | Un evento de riesgo registrado en el historial. |
#   S i s t e m a D e t e c c i o n  
 