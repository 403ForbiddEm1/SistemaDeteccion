/*
  Sistema de Detección Térmico-Gaseosa - Sketch para Arduino

  Envía cada segundo una línea por USB (9600 baudios):  T:28.4;G:312;F:0
    T = temperatura en °C (DHT11)
    G = gas, lectura analógica 0-1023 (MQ-2)
    F = flama (1 sí, 0 no)

  Solo se envían los sensores activados abajo: el que falte aparece "Sin señal" en el dashboard.
  Desde Java llegan comandos: 'M' = silenciar el buzzer, 'U' = reactivarlo.

  Biblioteca necesaria: "DHT sensor library" (Adafruit) + "Adafruit Unified Sensor".
*/
#include <DHT.h>

// ----- Qué sensores tienen conectados -----
#define USAR_TEMP  true
#define USAR_GAS   true
#define USAR_FLAMA false

// ----- Pines -----
#define PIN_DHT    2
#define PIN_FLAMA  3     // la mayoría de módulos de flama dan LOW cuando detectan fuego
#define PIN_GAS    A0
#define PIN_BUZZER 8
#define PIN_LED    9

// ----- Umbrales de la alarma física (deben coincidir con los de Java) -----
// El DHT11 solo mide de 0 a 50 °C, por eso el umbral crítico queda por debajo de 50.
const float TEMP_CRITICA = 40.0;
const int   GAS_CRITICO  = 600;

// El MQ-2 necesita calentarse: durante este tiempo no se dispara la alarma por gas.
const unsigned long CALENTAMIENTO_GAS_MS = 30000;

DHT dht(PIN_DHT, DHT11);
bool silenciada = false;
bool peligro = false;
bool sonando = false;
unsigned long ultimoEnvio = 0;

// Agrega un campo a la línea separándolo con ';' (sin ';' al final).
void agregar(String &linea, const String &campo) {
  if (linea.length() > 0) linea += ";";
  linea += campo;
}

void setup() {
  Serial.begin(9600);
  dht.begin();
  pinMode(PIN_FLAMA, INPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  pinMode(PIN_LED, OUTPUT);
}

void loop() {
  // Comandos desde la aplicación Java
  while (Serial.available()) {
    char c = Serial.read();
    if (c == 'M') silenciada = true;
    if (c == 'U') silenciada = false;
  }

  // Lectura y envío de sensores, una vez por segundo
  if (millis() - ultimoEnvio >= 1000) {
    ultimoEnvio = millis();
    peligro = false;
    String linea = "";

    if (USAR_TEMP) {
      float t = dht.readTemperature();
      if (!isnan(t)) {             // si el DHT falla, no se envía y queda "Sin señal"
        agregar(linea, "T:" + String(t, 1));
        if (t >= TEMP_CRITICA) peligro = true;
      }
    }
    if (USAR_GAS) {
      int g = analogRead(PIN_GAS);
      agregar(linea, "G:" + String(g));
      if (g >= GAS_CRITICO && millis() > CALENTAMIENTO_GAS_MS) peligro = true;
    }
    if (USAR_FLAMA) {
      bool f = digitalRead(PIN_FLAMA) == LOW;
      agregar(linea, "F:" + String(f ? 1 : 0));
      if (f) peligro = true;
    }

    if (!peligro) silenciada = false;   // la próxima alarma sí sonará
    if (linea.length() > 0) Serial.println(linea);
  }

  // Fuera del bloque de 1 s: responde al instante a 'M' y 'U'
  digitalWrite(PIN_LED, peligro ? HIGH : LOW);
  bool debeSonar = peligro && !silenciada;
  if (debeSonar != sonando) {
    sonando = debeSonar;
    if (sonando) tone(PIN_BUZZER, 1000);
    else noTone(PIN_BUZZER);
  }
}
